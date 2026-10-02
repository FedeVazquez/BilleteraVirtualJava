package com.dmh.accounts.exception;

/** Falta la autenticacion o no es valida (HTTP 401). */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
