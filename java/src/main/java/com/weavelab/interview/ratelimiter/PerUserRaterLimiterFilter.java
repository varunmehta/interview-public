package com.weavelab.interview.ratelimiter;

import com.weavelab.interview.auth.AuthFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;

import static com.weavelab.interview.ratelimiter.RateLimiterFilter.LIMIT_HEADER;
import static com.weavelab.interview.ratelimiter.RateLimiterFilter.REMAINING_HEADER;
import static com.weavelab.interview.ratelimiter.RateLimiterFilter.RESET_HEADER;

/**
 * Per-user leaky bucket: every user gets the same limit, but each user has their own bucket.
 * Runs after {@link AuthFilter} so it can key on the authenticated user id. Requests without a
 * valid user are passed through unlimited; {@code AuthInterceptor} rejects them on /api/*.
 */
@Component
@Order(2)
public class PerUserRaterLimiterFilter extends OncePerRequestFilter {

    static final String USER_KEY_PREFIX = "user:";

    private final RateLimiter rateLimiter;

    public PerUserRaterLimiterFilter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // /api/auth stays limited, as it can be abused too.
        return request.getRequestURI().equals("/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Object userId = request.getAttribute(AuthFilter.USER_ID_ATTRIBUTE);
        if (!(userId instanceof String user) || user.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        // Emails are case-insensitive; don't let case variants get separate buckets.
        RateLimitResult result = rateLimiter.tryAcquire(USER_KEY_PREFIX + user.toLowerCase(Locale.ROOT));

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
