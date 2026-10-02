package com.dmh.auth.dto;

import java.util.List;

/** Respuesta del endpoint interno de users-service con los datos para validar un login. */
public record CredentialsResponse(Long id, String email, String passwordHash, List<String> roles,
        boolean emailVerified) {
}
