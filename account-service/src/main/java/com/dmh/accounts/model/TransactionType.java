package com.dmh.accounts.model;

/** Tipo de movimiento. DEPOSIT y TRANSFER_IN son ingresos; TRANSFER_OUT es un egreso. */
public enum TransactionType {
    /** Ingreso de dinero desde una tarjeta propia. */
    DEPOSIT(true),
    /** Transferencia recibida de otra cuenta. */
    TRANSFER_IN(true),
    /** Transferencia enviada a otra cuenta. */
    TRANSFER_OUT(false);

    private final boolean income;

    TransactionType(boolean income) {
        this.income = income;
    }

    public boolean isIncome() {
        return income;
    }
}
