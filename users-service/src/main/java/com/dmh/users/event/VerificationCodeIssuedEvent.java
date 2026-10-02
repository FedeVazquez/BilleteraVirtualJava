package com.dmh.users.event;

/** Se genero un codigo de verificacion de email y hay que enviarlo por correo. */
public record VerificationCodeIssuedEvent(String email, String firstName, String code) {
}
