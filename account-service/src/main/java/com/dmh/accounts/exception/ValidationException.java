package com.dmh.accounts.exception;

/** Los datos de entrada no son validos (HTTP 400). */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
