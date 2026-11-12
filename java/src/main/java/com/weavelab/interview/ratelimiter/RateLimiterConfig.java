package com.weavelab.interview.ratelimiter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class RateLimiterConfig {

    @Bean
    public RateLimiter rateLimiter(@Value("${app.rate-limit.capacity}") int capacity,
                                   @Value("${app.rate-limit.period}") Duration period) {
        return new LeakyBucketRateLimiter(capacity, period, Clock.systemUTC());
    }
}
