package com.weavelab.interview.ratelimiter;

import org.junit.jupiter.api.Test;

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

class LeakyBucketRateLimiterTest {

    @Test
    void concurrentRequestsNeverExceedCapacity() throws Exception {
        // Fixed clock: nothing leaks during the test, so exactly `capacity` requests may pass.
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter(10, Duration.ofMinutes(1), clock);

        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return limiter.tryAcquire(RateLimiterFilter.GLOBAL_KEY).allowed();
                }));
            }
            start.countDown(); // release all threads at once

            int allowed = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(10);
        } finally {
            pool.shutdownNow();
        }
    }
}
