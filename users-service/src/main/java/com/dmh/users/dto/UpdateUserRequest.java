package com.dmh.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos personales editables. Todos son opcionales: solo se cambian los que vienen en el JSON.
 * Un valor presente no puede quedar vacio (las expresiones exigen al menos una letra o los digitos del telefono).
 */
public record UpdateUserRequest(
        @Schema(example = "Juan Manuel")
        @Size(max = 60, message = "El nombre no puede superar los 60 caracteres")
        @Pattern(regexp = NAME_REGEX, message = "El nombre debe tener al menos una letra y solo letras, espacios, apóstrofes y guiones")
        String firstName,

        @Schema(example = "Pérez")
        @Size(max = 60, message = "El apellido no puede superar los 60 caracteres")
        @Pattern(regexp = NAME_REGEX, message = "El apellido debe tener al menos una letra y solo letras, espacios, apóstrofes y guiones")
        String lastName,

        @Schema(example = "+5491155556666")
        @Pattern(regexp = "\\+?\\d{8,15}", message = "El teléfono debe tener entre 8 y 15 dígitos")
        String phone) {

    private static final String NAME_REGEX = "^(?=.*\\p{L})[\\p{L} .'-]+$";
}
