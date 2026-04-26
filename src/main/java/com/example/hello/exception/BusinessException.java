package com.example.hello.exception;

/**
 * Custom business exception for domain-specific errors.
 * Carries HTTP status code for proper API response mapping.
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        this(400, message);
    }

    public int getCode() { return code; }
}
