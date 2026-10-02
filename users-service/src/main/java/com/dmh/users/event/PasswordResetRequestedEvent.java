package com.dmh.users.event;

/** Se genero un token de recupero de contraseña y hay que enviar el link por correo. */
public record PasswordResetRequestedEvent(String email, String firstName, String token) {
}
