package com.dmh.accounts.security;

/** Usuario autenticado, armado a partir de los claims del JWT. */
public record AuthenticatedUser(Long id, String email) {
}
