package com.cimparato.csbm.aop.logging;

import org.springframework.boot.logging.LogLevel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface LogMethod {

    LogLevel level() default LogLevel.INFO;

    boolean logParams() default false;

    boolean logResult() default false;

    boolean measureTime() default false;

    // Messaggio personalizzato da includere nel log.
    String message() default "";
}
