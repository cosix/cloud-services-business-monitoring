package com.cimparato.csbm.web.rest.errors;

public class CsvMappingException extends RuntimeException {

    public CsvMappingException(String message) {
        super(message);
    }

    public CsvMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
