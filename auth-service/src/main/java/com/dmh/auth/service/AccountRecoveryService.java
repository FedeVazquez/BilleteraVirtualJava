package com.dmh.auth.service;

import org.springframework.stereotype.Service;

import com.dmh.auth.client.UsersClient;
import com.dmh.auth.dto.EmailRequest;
import com.dmh.auth.dto.ResetPasswordRequest;
import com.dmh.auth.dto.VerifyEmailRequest;
import com.dmh.auth.exception.ValidationException;

/**
 * Verificacion de email y recupero de contraseña. Los datos (codigos, tokens, contraseñas) viven en
 * users-service: este servicio expone los endpoints publicos y delega por Feign.
 */
@Service
public class AccountRecoveryService {

    private final UsersClient usersClient;
    private final AuthService authService;

    public AccountRecoveryService(UsersClient usersClient, AuthService authService) {
        this.usersClient = usersClient;
        this.authService = authService;
    }

    public void verifyEmail(VerifyEmailRequest request) {
        usersClient.confirmEmail(request);
    }

    public void resendVerificationCode(EmailRequest request) {
        usersClient.resendVerificationCode(request);
    }

    public void requestPasswordReset(EmailRequest request) {
        usersClient.requestPasswordReset(request);
    }

    public void resetPassword(ResetPasswordRequest request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new ValidationException("Las contraseñas no coinciden");
        }
        UsersClient.PasswordResetResult result = usersClient
                .confirmPasswordReset(new UsersClient.NewPasswordRequest(request.token(), request.password()));
        // Quien recupera la contraseña puede estar echando a un intruso: se cierran todas las sesiones abiertas.
        authService.revokeAllSessions(result.userId());
    }
}
