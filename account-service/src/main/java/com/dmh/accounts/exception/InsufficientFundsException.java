package com.dmh.accounts.exception;

/** El saldo disponible no alcanza para la operacion (HTTP 410, segun la consigna). */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String message) {
        super(message);
    }
}
