package com.dmh.accounts.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAccountRequest(
        @Schema(example = "sol.luna.mar")
        @NotBlank(message = "El alias es obligatorio")
        @Size(max = 60, message = "El alias no puede superar los 60 caracteres")
        // Igual que el alias generado: tres palabras separadas por punto (se admiten letras y numeros).
        @Pattern(regexp = "^[A-Za-z0-9]+\\.[A-Za-z0-9]+\\.[A-Za-z0-9]+$",
                message = "El alias debe tener tres palabras separadas por punto (letras y números)")
        String alias) {
}
