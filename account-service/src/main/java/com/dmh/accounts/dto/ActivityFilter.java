package com.dmh.accounts.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.dmh.accounts.dto.TransactionResponse.Direction;

import org.springframework.format.annotation.DateTimeFormat;

import io.swagger.v3.oas.annotations.Parameter;

/**
 * Filtros opcionales de la actividad (opcional del sprint 3). Todos se pueden combinar.
 * <p>
 * Los rangos de monto que propone la consigna se arman con {@code minAmount} y {@code maxAmount}:
 * $0 a $1.000, $1.000 a $5.000, $5.000 a $20.000, $20.000 a $100.000 y más de $100.000 (solo minAmount).
 */
public record ActivityFilter(
        @Parameter(description = "INCOME (ingresos) o EXPENSE (egresos)")
        Direction direction,

        @Parameter(description = "Desde esta fecha inclusive (AAAA-MM-DD)", example = "2026-10-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate from,

        @Parameter(description = "Hasta esta fecha inclusive (AAAA-MM-DD)", example = "2026-10-31")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate to,

        @Parameter(description = "Monto mínimo inclusive", example = "1000")
        BigDecimal minAmount,

        @Parameter(description = "Monto máximo inclusive", example = "5000")
        BigDecimal maxAmount) {
}
