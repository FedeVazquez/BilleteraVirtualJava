package com.dmh.accounts.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /accounts/{id}/transferences}. La consigna usa el mismo endpoint para dos
 * operaciones, y el cuerpo indica cual es:
 * <ul>
 * <li>Ingreso de dinero desde una tarjeta propia (sprint 3): {@code cardId} + {@code amount}.</li>
 * <li>Transferencia a otra cuenta (sprint 4): {@code destination} (CVU, CBU o alias) + {@code amount}.</li>
 * </ul>
 */
public record TransferenceRequest(
        @Schema(description = "Tarjeta propia desde la que se ingresa dinero", example = "1")
        Long cardId,

        @Schema(description = "CVU/CBU (22 dígitos) o alias de la cuenta destino", example = "sol.luna.mar")
        @Size(max = 100, message = "El destino no puede superar los 100 caracteres")
        String destination,

        @Schema(example = "1500.50")
        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
        @DecimalMax(value = "1000000.00", message = "El monto máximo por operación es 1.000.000")
        @Digits(integer = 7, fraction = 2, message = "El monto admite hasta 2 decimales")
        BigDecimal amount,

        @Schema(description = "Motivo de la transferencia (opcional)", example = "Alquiler")
        @Size(max = 100, message = "La descripción no puede superar los 100 caracteres")
        String description) {

    @Schema(hidden = true)
    @AssertTrue(message = "Indique cardId (ingreso desde tarjeta) o destination (transferencia), pero no ambos")
    public boolean isOneOperation() {
        boolean hasDestination = destination != null && !destination.isBlank();
        return (cardId != null) != hasDestination;
    }

    public boolean isDeposit() {
        return cardId != null;
    }
}
