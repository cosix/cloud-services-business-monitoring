package com.cimparato.csbm.aop.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LogLevel;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.lang.reflect.Method;
import java.util.Arrays;

@Aspect
@Component
public class LoggingAspect {

    @Around("@annotation(logging.aop.com.cimparato.csbm.LogMethod)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {

        // recupera il logger per la classe target
        Logger logger = LoggerFactory.getLogger(joinPoint.getTarget().getClass());

        // recupera firma del metodo e annotazione
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        LogMethod annotation = method.getAnnotation(LogMethod.class);

        // crea il messaggio
        String methodName = signature.getDeclaringType().getSimpleName() + "." + signature.getName();
        String message = annotation.message().isEmpty() ? "Executing method" : annotation.message();

        // Log dei parametri opzionale
        if (annotation.logParams()) {
            String params = Arrays.toString(joinPoint.getArgs());
            logWithLevel(logger, annotation.level(), "{} {} - Parameters: {}", message, methodName, params);
        } else {
            logWithLevel(logger, annotation.level(), "{} {} - Start", message, methodName);
        }

        // Misura il tempo di esecuzione
        StopWatch stopWatch = null;
        if (annotation.measureTime()) {
            stopWatch = new StopWatch();
            stopWatch.start();
        }

        try {

            Object result = joinPoint.proceed();

            // messaggio di completamento
            StringBuilder completionMessage = new StringBuilder();
            completionMessage.append(message).append(" ").append(methodName).append(" - Completed");

            if (stopWatch != null) {
                stopWatch.stop();
                completionMessage.append(" in ").append(stopWatch.getTotalTimeMillis()).append("ms");
            }

            if (annotation.logResult()) {
                if (!void.class.equals(method.getReturnType()) && result != null) {
                    completionMessage.append(" with result: ").append(result);
                } else if (!void.class.equals(method.getReturnType())) {
                    completionMessage.append(" with result: null");
                } else {
                    completionMessage.append(" (void method)");
                }
            }

            logWithLevel(logger, annotation.level(), completionMessage.toString());

            return result;

        } catch (Throwable ex) {

            // messaggio di errore
            StringBuilder errorMessage = new StringBuilder();
            errorMessage.append(message).append(" ").append(methodName).append(" - Failed");

            if (stopWatch != null) {
                stopWatch.stop();
                errorMessage.append(" after ").append(stopWatch.getTotalTimeMillis()).append("ms");
            }

            errorMessage.append(" with exception: ").append(ex.getMessage());

            logger.error(errorMessage.toString(), ex);
            throw ex;
        }
    }

    private void logWithLevel(Logger logger, LogLevel level, String format, Object... arguments) {
        switch (level) {
            case TRACE:
                if (logger.isTraceEnabled()) {
                    logger.trace(format, arguments);
                }
                break;
            case DEBUG:
                if (logger.isDebugEnabled()) {
                    logger.debug(format, arguments);
                }
                break;
            case INFO:
                if (logger.isInfoEnabled()) {
                    logger.info(format, arguments);
                }
                break;
            case WARN:
                if (logger.isWarnEnabled()) {
                    logger.warn(format, arguments);
                }
                break;
            case ERROR:
                if (logger.isErrorEnabled()) {
                    logger.error(format, arguments);
                }
                break;
        }
    }
}
