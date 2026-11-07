package com.weavelab.interview.ratelimiter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Global variant: one bucket shared by every client. No longer registered as a Spring bean;
 * replaced by {@link PerUserRaterLimiterFilter}. Kept for reference and its tests.
 */
public class RateLimiterFilter extends OncePerRequestFilter {

    // Single shared bucket: every client gets the same global limit.
    static final String GLOBAL_KEY = "global";

    static final String LIMIT_HEADER = "X-RateLimit-Limit";
    static final String REMAINING_HEADER = "X-RateLimit-Remaining";
    static final String RESET_HEADER = "X-RateLimit-Reset";

    private final RateLimiter rateLimiter;

    public RateLimiterFilter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Keeping /api/auth behind the leaky bucket, as someone can also end up abusing it...
        return path.equals("/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimitResult result = rateLimiter.tryAcquire(GLOBAL_KEY);

        response.setHeader(LIMIT_HEADER, String.valueOf(result.limit()));
        response.setHeader(REMAINING_HEADER, String.valueOf(result.remaining()));
        response.setHeader(RESET_HEADER, String.valueOf(toSeconds(result.resetAfter())));

        if (!result.allowed()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(toSeconds(result.retryAfter())));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too Many Requests\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    // Round up so clients never retry too early.
    private static long toSeconds(Duration d) {
        return (d.toMillis() + 999) / 1000;
    }
}
