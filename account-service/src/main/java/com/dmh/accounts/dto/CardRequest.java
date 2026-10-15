package com.dmh.accounts.dto;

import com.dmh.accounts.model.CardType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CardRequest(
        @Schema(example = "4509953566233704", description = "Número de la tarjeta, sin espacios")
        @NotBlank(message = "El número de tarjeta es obligatorio")
        @Pattern(regexp = "\\d{13,19}", message = "El número de tarjeta debe tener entre 13 y 19 dígitos")
        String number,

        @Schema(example = "JUAN PEREZ")
        @NotBlank(message = "El nombre del titular es obligatorio")
        @Size(max = 60, message = "El nombre del titular no puede superar los 60 caracteres")
        @Pattern(regexp = "^[\\p{L} .'-]*$", message = "El nombre del titular solo puede tener letras y espacios")
        String holderName,

        @Schema(example = "11/29", description = "Vencimiento en formato MM/AA")
        @NotBlank(message = "El vencimiento es obligatorio")
        @Pattern(regexp = "(0[1-9]|1[0-2])/\\d{2}", message = "El vencimiento debe tener el formato MM/AA")
        String expiration,

        @Schema(example = "123", description = "Se valida el formato pero nunca se guarda")
        @NotBlank(message = "El código de seguridad es obligatorio")
        @Pattern(regexp = "\\d{3,4}", message = "El código de seguridad debe tener 3 o 4 dígitos")
        String cvv,

        @Schema(example = "DEBIT")
        @NotNull(message = "El tipo de tarjeta es obligatorio (DEBIT o CREDIT)")
        CardType type) {
}
