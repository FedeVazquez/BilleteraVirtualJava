package com.dmh.auth.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.dmh.auth.dto.CredentialsResponse;
import com.dmh.auth.dto.EmailRequest;
import com.dmh.auth.dto.VerifyEmailRequest;

/**
 * Cliente Feign de users-service. "users-service" es el nombre con el que el servicio se registra
 * en Eureka: Feign resuelve la direccion real consultando el registro y balancea entre instancias.
 */
@FeignClient(name = "users-service", configuration = InternalFeignConfig.class)
public interface UsersClient {

    @GetMapping("/internal/users/credentials")
    CredentialsResponse getCredentials(@RequestParam("email") String email);

    @PostMapping("/internal/users/email-verification/confirm")
    void confirmEmail(@RequestBody VerifyEmailRequest request);

    @PostMapping("/internal/users/email-verification/resend")
    void resendVerificationCode(@RequestBody EmailRequest request);

    @PostMapping("/internal/users/password-reset/request")
    void requestPasswordReset(@RequestBody EmailRequest request);

    @PostMapping("/internal/users/password-reset/confirm")
    PasswordResetResult confirmPasswordReset(@RequestBody NewPasswordRequest request);

    /** Cuerpo que espera users-service para confirmar el cambio de contraseña. */
    record NewPasswordRequest(String token, String newPassword) {
    }

    /** Usuario al que se le cambio la contraseña. */
    record PasswordResetResult(Long userId) {
    }
}
