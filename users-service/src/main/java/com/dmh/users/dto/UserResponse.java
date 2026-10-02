package com.dmh.users.dto;

import com.dmh.users.model.User;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Datos del usuario que se devuelven al cliente. Nunca incluye la contraseña. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponse(Long id, String firstName, String lastName, String dni, String email, String phone,
        String cvu, String alias) {

    public static UserResponse from(User user) {
        return from(user, null);
    }

    public static UserResponse from(User user, AccountResponse account) {
        return new UserResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getDni(),
                user.getEmail(), user.getPhone(), account == null ? null : account.cvu(),
                account == null ? null : account.alias());
    }
}
