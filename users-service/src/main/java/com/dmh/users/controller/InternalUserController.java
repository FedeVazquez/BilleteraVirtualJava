package com.dmh.users.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dmh.users.dto.CredentialsResponse;
import com.dmh.users.dto.EmailRequest;
import com.dmh.users.dto.ResetPasswordRequest;
import com.dmh.users.dto.VerifyEmailRequest;
import com.dmh.users.service.EmailVerificationService;
import com.dmh.users.service.PasswordResetService;
import com.dmh.users.service.UserService;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;

/**
 * Endpoints de uso interno entre microservicios (los consume auth-service por Feign).
 * No se publican en el API Gateway y exigen el header X-Internal-Api-Key.
 */
@Hidden
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserService userService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;

    public InternalUserController(UserService userService, EmailVerificationService emailVerificationService,
            PasswordResetService passwordResetService) {
        this.userService = userService;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/credentials")
    public CredentialsResponse credentials(@RequestParam String email) {
        return userService.getCredentials(email);
    }

    @PostMapping("/email-verification/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.confirm(request.email(), request.code());
    }

    @PostMapping("/email-verification/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerificationCode(@Valid @RequestBody EmailRequest request) {
        emailVerificationService.resend(request.email());
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestPasswordReset(@Valid @RequestBody EmailRequest request) {
        passwordResetService.request(request.email());
    }

    @PostMapping("/password-reset/confirm")
    public PasswordResetResult confirmPasswordReset(@Valid @RequestBody ResetPasswordRequest request) {
        return new PasswordResetResult(passwordResetService.confirm(request.token(), request.newPassword()));
    }

    /** Usuario al que se le cambio la contraseña. */
    public record PasswordResetResult(Long userId) {
    }
}
