package com.weavelab.interview.ratelimiter;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory leaky bucket. Each request adds one token to the bucket for its key; the bucket
 * drains at {@code capacity / period}. A request is rejected if it would overflow the bucket.
 */
public class LeakyBucketRateLimiter implements RateLimiter {

    private final int capacity;
    private final double leakPerMillis;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public LeakyBucketRateLimiter(int capacity, Duration period, Clock clock) {
        if (capacity <= 0 || period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("capacity and period must be positive");
        }
        this.capacity = capacity;
        this.leakPerMillis = (double) capacity / period.toMillis();
        this.clock = clock;
    }

    @Override
    public RateLimitResult tryAcquire(String key) {
        return buckets.computeIfAbsent(key, k -> new Bucket(clock.millis())).tryAdd();
    }

    private Duration timeToLeak(double tokens) {
        return Duration.ofMillis((long) Math.ceil(Math.max(0, tokens) / leakPerMillis));
    }

    private final class Bucket {
        private double level;
        private long lastLeakMillis;

        Bucket(long nowMillis) {
            this.lastLeakMillis = nowMillis;
        }

        synchronized RateLimitResult tryAdd() {
            long now = clock.millis();
            level = Math.max(0, level - (now - lastLeakMillis) * leakPerMillis);
            lastLeakMillis = now;

            boolean allowed = level + 1 <= capacity;
            if (allowed) {
                level += 1;
            }
            int remaining = (int) Math.floor(capacity - level);
            Duration retryAfter = allowed ? Duration.ZERO : timeToLeak(level + 1 - capacity);
            return new RateLimitResult(allowed, capacity, remaining, retryAfter, timeToLeak(level));
        }
    }
}
