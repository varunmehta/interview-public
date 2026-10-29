package com.weavelab.interview.ratelimiter;

/**
 * Decides whether a request identified by {@code key} may proceed.
 * Swap the implementation (e.g. Caffeine- or Redis-backed) without touching the filter.
 */
public interface RateLimiter {

    RateLimitResult tryAcquire(String key);
}
