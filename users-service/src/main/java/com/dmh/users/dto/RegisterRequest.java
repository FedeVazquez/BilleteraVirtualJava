package com.dmh.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Schema(example = "Juan")
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 60, message = "El nombre no puede superar los 60 caracteres")
        @Pattern(regexp = NAME_REGEX, message = "El nombre solo puede tener letras, espacios, apóstrofes y guiones")
        String firstName,

        @Schema(example = "Pérez")
        @NotBlank(message = "El apellido es obligatorio")
        @Size(max = 60, message = "El apellido no puede superar los 60 caracteres")
        @Pattern(regexp = NAME_REGEX, message = "El apellido solo puede tener letras, espacios, apóstrofes y guiones")
        String lastName,

        @Schema(example = "30123456")
        @NotBlank(message = "El DNI es obligatorio")
        @Pattern(regexp = "\\d{7,8}", message = "El DNI debe tener 7 u 8 dígitos")
        String dni,

        @Schema(example = "juan.perez@mail.com")
        @NotBlank(message = "El email es obligatorio")
        // El regexp se suma a la validacion estandar para exigir un dominio con punto (rechaza "a@b").
        @Email(regexp = ".+@.+\\..+", message = "El email no tiene un formato válido")
        @Size(max = 120, message = "El email no puede superar los 120 caracteres")
        String email,

        @Schema(example = "+5491122334455")
        @NotBlank(message = "El teléfono es obligatorio")
        @Pattern(regexp = "\\+?\\d{8,15}", message = "El teléfono debe tener entre 8 y 15 dígitos")
        String phone,

        @Schema(example = "Clave1234")
        @NotBlank(message = "La contraseña es obligatoria")
        // BCrypt solo usa los primeros 72 bytes: se limita para no aceptar claves que se truncan.
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        String password) {

    /** Letras de cualquier idioma (incluye acentos y ñ), espacios, punto, apóstrofe y guion. Vacío lo cubre @NotBlank. */
    private static final String NAME_REGEX = "^[\\p{L} .'-]*$";
}
