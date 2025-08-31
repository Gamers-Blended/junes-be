package com.gamersblended.junes.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    // Number of requests allowed
    int requests() default 5;

    // Time window duration
    int duration() default 1;

    // Time unit for duration
    TimeUnit timeUnit() default TimeUnit.MINUTES;

    // Custom key for rate limiting (optional)
    // If not specified, use IP address
    String key() default "";

    // Whether to use per-user rate limiting
    // If true, append user identifier to the key
    boolean perUser() default false;
}
