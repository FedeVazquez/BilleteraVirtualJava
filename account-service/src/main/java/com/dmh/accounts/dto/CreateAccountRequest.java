package com.dmh.accounts.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateAccountRequest(
        @NotNull(message = "El id de usuario es obligatorio")
        @Positive(message = "El id de usuario debe ser positivo")
        Long userId) {
}
