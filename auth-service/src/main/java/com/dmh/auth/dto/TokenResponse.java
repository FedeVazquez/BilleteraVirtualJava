package com.dmh.auth.dto;

/** Respuesta del login: el JWT, su tipo y en cuantos segundos vence. */
public record TokenResponse(String token, String tokenType, long expiresIn) {

    public static TokenResponse bearer(String token, long expiresIn) {
        return new TokenResponse(token, "Bearer", expiresIn);
    }
}
