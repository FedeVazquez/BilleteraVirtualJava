package com.dmh.auth.exception;

/** Las credenciales son correctas pero el usuario todavia no verifico su email (HTTP 403). */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
