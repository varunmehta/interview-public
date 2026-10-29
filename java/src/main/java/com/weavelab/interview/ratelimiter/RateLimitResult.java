package com.weavelab.interview.ratelimiter;

import java.time.Duration;

/**
 * Outcome of a rate limit check.
 *
 * @param allowed    whether the request may proceed
 * @param limit      maximum requests allowed per period
 * @param remaining  requests still allowed right now
 * @param retryAfter how long until the next request would be allowed (zero if
 *                   allowed)
 * @param resetAfter how long until the bucket is fully drained
 */
public record RateLimitResult(boolean allowed, int limit, int remaining, Duration retryAfter, Duration resetAfter) {
}
