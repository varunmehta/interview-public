package com.weavelab.interview.ratelimiter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterFilterTest {

    private MutableClock clock;
    private RateLimiterFilter filter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        filter = new RateLimiterFilter(new LeakyBucketRateLimiter(10, Duration.ofMinutes(1), clock));
    }

    @Test
    void allowsTenRequestsPerMinuteThenRejects() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(send("/api/contacts")).isEqualTo(200);
        }
        assertThat(send("/api/contacts")).isEqualTo(429);
    }

    @Test
    void limitIsSharedAcrossClients() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(send("/api/contacts", "user" + i + "@example.com")).isEqualTo(200);
        }
        assertThat(send("/api/contacts", "someone-else@example.com")).isEqualTo(429);
    }

    @Test
    void bucketLeaksOverTime() throws Exception {
        for (int i = 0; i < 10; i++) {
            send("/api/contacts");
        }
        assertThat(send("/api/contacts")).isEqualTo(429);

        clock.advance(Duration.ofSeconds(6)); // one request's worth leaks out
        assertThat(send("/api/contacts")).isEqualTo(200);
        assertThat(send("/api/contacts")).isEqualTo(429);

        clock.advance(Duration.ofMinutes(1));
        for (int i = 0; i < 10; i++) {
            assertThat(send("/api/contacts")).isEqualTo(200);
        }
    }

    @Test
    void healthIsNotRateLimitedButAuthIs() throws Exception {
        for (int i = 0; i < 10; i++) {
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

        for (int i = 0; i < 9; i++) {
            send("/api/contacts");
        }
        MockHttpServletResponse rejected = call("/api/contacts", "user@example.com");
        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(rejected.getHeader("X-RateLimit-Reset")).isEqualTo("60");
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("6");
    }

    private int send(String path) throws Exception {
        return send(path, "user@example.com");
    }

    private int send(String path, String user) throws Exception {
        return call(path, user).getStatus();
    }

    private MockHttpServletResponse call(String path, String user) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("Authorization", "Bearer " + user);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

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
