package com.weavelab.interview.ratelimiter;

import com.weavelab.interview.auth.AuthFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class PerUserRaterLimiterTest {

    private static final int LIMIT = 10;
    private static final int ABUSE = 20;

    private final AuthFilter authFilter = new AuthFilter();
    private MutableClock clock;
    private PerUserRaterLimiterFilter filter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        filter = new PerUserRaterLimiterFilter(new LeakyBucketRateLimiter(LIMIT, Duration.ofMinutes(1), clock));
    }

    // --- Carried over from RateLimiterFilterTest ---

    @Test
    void allowsTenRequestsPerMinuteThenRejects() throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            assertThat(send("/api/contacts")).isEqualTo(200);
        }
        assertThat(send("/api/contacts")).isEqualTo(429);
    }

    @Test
    void bucketLeaksOverTime() throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            send("/api/contacts");
        }
        assertThat(send("/api/contacts")).isEqualTo(429);

        clock.advance(Duration.ofSeconds(6)); // one request's worth leaks out
        assertThat(send("/api/contacts")).isEqualTo(200);
        assertThat(send("/api/contacts")).isEqualTo(429);

        clock.advance(Duration.ofMinutes(1));
        for (int i = 0; i < LIMIT; i++) {
            assertThat(send("/api/contacts")).isEqualTo(200);
        }
    }

    @Test
    void healthIsNotRateLimitedButAuthIs() throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            send("/api/contacts");
        }
        assertThat(send("/api/contacts")).isEqualTo(429);

        assertThat(send("/health")).isEqualTo(200);
        assertThat(send("/api/auth/token")).isEqualTo(429);
    }

    @Test
    void setsRateLimitHeaders() throws Exception {
        MockHttpServletResponse first = call("/api/contacts", "user@example.com");
        assertThat(first.getHeader("X-RateLimit-Limit")).isEqualTo("10");
        assertThat(first.getHeader("X-RateLimit-Remaining")).isEqualTo("9");
        assertThat(first.getHeader("X-RateLimit-Reset")).isEqualTo("6");
        assertThat(first.getHeader("Retry-After")).isNull();

        for (int i = 0; i < LIMIT - 1; i++) {
            send("/api/contacts");
        }
        MockHttpServletResponse rejected = call("/api/contacts", "user@example.com");
        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(rejected.getHeader("X-RateLimit-Reset")).isEqualTo("60");
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("6");
    }

    // --- Per-user behavior (replaces limitIsSharedAcrossClients) ---

    @Test
    void limitIsTrackedPerUser() throws Exception {
        for (int u = 0; u < 10; u++) {
            String user = "user" + u + "@example.com";
            for (int i = 0; i < LIMIT; i++) {
                assertThat(send("/api/contacts", user)).isEqualTo(200);
            }
        }
        for (int u = 0; u < 10; u++) {
            assertThat(send("/api/contacts", "user" + u + "@example.com")).isEqualTo(429);
        }
    }

    @Test
    void eachUserGetsSameLimit() throws Exception {
        for (String user : List.of("a@example.com", "b@example.com", "c@example.com")) {
            MockHttpServletResponse response = call("/api/contacts", user);
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("10");
            assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("9");
        }
    }

    @Test
    void userKeyIsCaseInsensitive() throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            assertThat(send("/api/contacts", "user@example.com")).isEqualTo(200);
        }
        assertThat(send("/api/contacts", "USER@Example.com")).isEqualTo(429);
    }

    // --- Abuse: 20 requests against a limit of 10 ---

    @Test
    void abusiveUserSending20RequestsGets10AllowedAnd10Rejected() throws Exception {
        int allowed = 0;
        int rejected = 0;
        for (int i = 0; i < ABUSE; i++) {
            MockHttpServletResponse response = call("/api/contacts", "abuser@example.com");
            if (response.getStatus() == 200) {
                allowed++;
                assertThat(response.getHeader("Retry-After")).isNull();
            } else {
                assertThat(response.getStatus()).isEqualTo(429);
                assertThat(response.getHeader("Retry-After")).isNotNull();
                assertThat(response.getContentAsString()).contains("Too Many Requests");
                rejected++;
            }
        }
        assertThat(allowed).isEqualTo(LIMIT);
        assertThat(rejected).isEqualTo(ABUSE - LIMIT);
    }

    @Test
    void abusiveUserDoesNotAffectOtherUsers() throws Exception {
        for (int i = 0; i < ABUSE; i++) {
            send("/api/contacts", "abuser@example.com");
        }
        assertThat(send("/api/contacts", "abuser@example.com")).isEqualTo(429);

        for (int i = 0; i < LIMIT; i++) {
            MockHttpServletResponse response = call("/api/contacts", "victim@example.com");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo(String.valueOf(LIMIT - 1 - i));
        }
        assertThat(send("/api/contacts", "victim@example.com")).isEqualTo(429);
    }

    @Test
    void rejectedRequestsDoNotExtendPenalty() throws Exception {
        for (int i = 0; i < ABUSE; i++) {
            send("/api/contacts", "abuser@example.com");
        }
        // Rejected requests never entered the bucket, so one leak interval frees exactly one slot.
        clock.advance(Duration.ofSeconds(6));
        assertThat(send("/api/contacts", "abuser@example.com")).isEqualTo(200);
        assertThat(send("/api/contacts", "abuser@example.com")).isEqualTo(429);
    }

    @Test
    void unauthenticatedRequestsAreNotRateLimited() throws Exception {
        for (int i = 0; i < ABUSE; i++) {
            MockHttpServletResponse noToken = call("/api/contacts", null);
            assertThat(noToken.getStatus()).isEqualTo(200); // reaches the chain; AuthInterceptor 401s it later
            assertThat(noToken.getHeader("X-RateLimit-Limit")).isNull();

            MockHttpServletResponse badToken = call("/api/contacts", "not-an-email");
            assertThat(badToken.getStatus()).isEqualTo(200);
            assertThat(badToken.getHeader("X-RateLimit-Limit")).isNull();
        }
        MockHttpServletResponse real = call("/api/contacts", "user@example.com");
        assertThat(real.getStatus()).isEqualTo(200);
        assertThat(real.getHeader("X-RateLimit-Remaining")).isEqualTo("9");
    }

    @Test
    void concurrentAbuseFromTwoUsersIsLimitedPerUser() throws Exception {
        List<String> users = List.of("a@example.com", "b@example.com");
        int threads = ABUSE * users.size();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> aResults = new ArrayList<>();
        List<Future<Integer>> bResults = new ArrayList<>();
        try {
            for (int i = 0; i < ABUSE; i++) {
                aResults.add(pool.submit(() -> { start.await(); return send("/api/contacts", users.get(0)); }));
                bResults.add(pool.submit(() -> { start.await(); return send("/api/contacts", users.get(1)); }));
            }
            start.countDown(); // fixed clock: nothing leaks while these race

            assertThat(countOk(aResults)).isEqualTo(LIMIT);
            assertThat(countOk(bResults)).isEqualTo(LIMIT);
        } finally {
            pool.shutdownNow();
        }
    }

    private static int countOk(List<Future<Integer>> results) throws Exception {
        int ok = 0;
        for (Future<Integer> r : results) {
            if (r.get() == 200) {
                ok++;
            }
        }
        return ok;
    }

    private int send(String path) throws Exception {
        return send(path, "user@example.com");
    }

    private int send(String path, String user) throws Exception {
        return call(path, user).getStatus();
    }

    // Runs the real AuthFilter first, mirroring production filter order (AuthFilter @Order(1) -> this @Order(2)).
    private MockHttpServletResponse call(String path, String user) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (user != null) {
            request.addHeader("Authorization", "Bearer " + user);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        authFilter.doFilter(request, response, (rq, rs) -> filter.doFilter(rq, rs, new MockFilterChain()));
        return response;
    }

    private static class MutableClock extends Clock {
        private volatile Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
