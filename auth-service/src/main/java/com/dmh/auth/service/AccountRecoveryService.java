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

    public AccountRecoveryService(UsersClient usersClient) {
        this.usersClient = usersClient;
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
        usersClient.confirmPasswordReset(new UsersClient.NewPasswordRequest(request.token(), request.password()));
    }
}
