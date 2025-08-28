package com.cimparato.csbm.web.rest.errors;

import com.cimparato.csbm.util.ResponseWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import javax.naming.SizeLimitExceededException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ResponseWrapper<Void>> handleMaxSizeException(MaxUploadSizeExceededException exc) {
        var message = "File size exceeds the maximum allowed limit (10MB)";
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ResponseWrapper<>(false, message, null));
    }

    @ExceptionHandler(SizeLimitExceededException.class)
    public ResponseEntity<ResponseWrapper<Void>> handleSizeLimitExceededException(SizeLimitExceededException ex) {
        log.warn("File size limit exceeced error: {}", ex.getMessage());
        var message = "The total request size exceeds the maximum allowed limit (10MB)";
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ResponseWrapper<>(false, message, null));
    }

    @ExceptionHandler(FileValidationException.class)
    public ResponseEntity<ResponseWrapper<Void>> handleFileValidationException(FileValidationException ex) {
        log.warn("File validation error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ResponseWrapper<>(false, ex.getMessage(), null));
    }

    @ExceptionHandler(FileProcessingException.class)
    public ResponseEntity<ResponseWrapper<Void>> handleFileProcessingException(FileProcessingException ex) {
        log.error("File processing error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ResponseWrapper<>(false, ex.getMessage(), null));
    }

    @ExceptionHandler(DuplicateFileException.class)
    public ResponseEntity<ResponseWrapper<Void>> handleDuplicateFileException(DuplicateFileException ex) {
        log.warn("Duplicate file error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ResponseWrapper<>(false, ex.getMessage(), null));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ResponseWrapper<Void>> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ResponseWrapper<>(false, ex.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseWrapper<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation error: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ResponseWrapper<>(false, "Validation error", errors));
    }

    @ExceptionHandler(ReportGenerationException.class)
    public ResponseEntity<ResponseWrapper<String>> handleReportGenerationException(
            ReportGenerationException ex, WebRequest request) {
        String requestDescription = request.getDescription(false);
        log.error("Report generation error: {} for request: {}", ex.getMessage(), requestDescription, ex);
        String details = "Failed to generate report. Request: " + requestDescription + ", Time: " + LocalDateTime.now();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ResponseWrapper<>(false, ex.getMessage(), details));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> handleGlobalException(
            Exception ex, WebRequest request) {
        String requestDescription = request.getDescription(false);
        log.error("Unexpected error: {} for request: {}", ex.getMessage(), requestDescription, ex);

        Map<String, Object> details = new HashMap<>();
        details.put("request", requestDescription);
        details.put("timestamp", LocalDateTime.now().toString());
        details.put("exceptionType", ex.getClass().getName());
        details.put("exceptionMessage", ex.getMessage());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ResponseWrapper<>(false, "An unexpected error occurred", details));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResponseWrapper<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        var message = "Access Denied: You do not have the necessary permissions to access this resource";
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ResponseWrapper<>(false, message, null));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ResponseWrapper<Void>> handleAuthenticationException(AuthenticationException ex) {
        var message = "Authentication error: " + ex.getMessage();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ResponseWrapper<>(false, message, null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResponseWrapper<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ResponseWrapper<>(false, ex.getMessage(),null));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex) {

        Map<String, Object> details = new HashMap<>();
        details.put("paramName", ex.getName());
        details.put("paramValue", ex.getValue());
        details.put("errorMessage", ex.getMessage());

        // Gestione specifica per gli enum
        if (ex.getRequiredType() != null && ex.getRequiredType().isEnum()) {
            details.put("requiredType", ex.getRequiredType().getSimpleName());
            details.put("allowedValues", Arrays.stream(ex.getRequiredType().getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.toList()));
        }

        String message = String.format("Parameter '%s' with value '%s' is invalid. Expected type: %s",
                ex.getName(), ex.getValue(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");

        log.warn("Parameter validation error: {}", message);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ResponseWrapper<>(false, message, details));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ResponseWrapper<Map<String, Object>>> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex) {

        Map<String, Object> details = new HashMap<>();
        details.put("paramName", ex.getParameterName());
        details.put("paramType", ex.getParameterType());

        String message = String.format("Required parameter '%s' of type '%s' is missing",
                ex.getParameterName(), ex.getParameterType());

        log.warn("Missing parameter error: {}", message);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ResponseWrapper<>(false, message, details));
    }
}
