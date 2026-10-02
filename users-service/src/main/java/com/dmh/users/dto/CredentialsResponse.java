package com.dmh.users.dto;

import java.util.List;

import com.dmh.users.model.Role;
import com.dmh.users.model.User;

/** Datos que necesita auth-service para validar un login. Solo se expone por el endpoint interno. */
public record CredentialsResponse(Long id, String email, String passwordHash, List<String> roles,
        boolean emailVerified) {

    public static CredentialsResponse from(User user) {
        return new CredentialsResponse(user.getId(), user.getEmail(), user.getPassword(),
                user.getRoles().stream().map(Role::getName).sorted().toList(), user.isEmailVerified());
    }
}
