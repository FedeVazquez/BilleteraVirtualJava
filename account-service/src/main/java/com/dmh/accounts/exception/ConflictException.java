package com.dmh.accounts.exception;

/** El recurso entra en conflicto con otro existente, por ejemplo una tarjeta ya asociada (HTTP 409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
