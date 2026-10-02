package com.dmh.auth.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dmh.auth.dto.EmailRequest;
import com.dmh.auth.dto.LoginRequest;
import com.dmh.auth.dto.MessageResponse;
import com.dmh.auth.dto.ResetPasswordRequest;
import com.dmh.auth.dto.TokenInfoResponse;
import com.dmh.auth.dto.TokenResponse;
import com.dmh.auth.dto.VerifyEmailRequest;
import com.dmh.auth.service.AccountRecoveryService;
import com.dmh.auth.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Autenticación")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final AccountRecoveryService accountRecoveryService;

    public AuthController(AuthService authService, AccountRecoveryService accountRecoveryService) {
        this.authService = authService;
        this.accountRecoveryService = accountRecoveryService;
    }

    @Operation(summary = "Login", description = "Valida email y contraseña y devuelve un token JWT.")
    @ApiResponse(responseCode = "200", description = "Login correcto: devuelve el token")
    @ApiResponse(responseCode = "400", description = "Contraseña incorrecta o datos inválidos")
    @ApiResponse(responseCode = "403", description = "El email todavía no fue verificado")
    @ApiResponse(responseCode = "404", description = "Usuario inexistente")
    @ApiResponse(responseCode = "500", description = "Error interno")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "Valida un token",
            description = "Devuelve los datos del usuario si el token es válido, no venció y no fue invalidado "
                    + "por un logout. Lo usa el API Gateway antes de dejar pasar cada petición.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Token válido")
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido, vencido o invalidado")
    @GetMapping("/validate")
    public TokenInfoResponse validate(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return authService.validate(authorization);
    }

    @Operation(summary = "Verifica el email con el código de 6 dígitos",
            description = "El código se envía por mail al registrarse. Hasta verificarlo no se puede hacer login.")
    @ApiResponse(responseCode = "200", description = "Email verificado")
    @ApiResponse(responseCode = "400", description = "Código incorrecto o vencido")
    @ApiResponse(responseCode = "404", description = "Usuario inexistente")
    @PostMapping("/verify-email")
    public MessageResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        accountRecoveryService.verifyEmail(request);
        return new MessageResponse("Email verificado. Ya puede iniciar sesión.");
    }

    @Operation(summary = "Reenvía el código de verificación de email")
    @PostMapping("/verify-email/resend")
    public MessageResponse resendVerificationCode(@Valid @RequestBody EmailRequest request) {
        accountRecoveryService.resendVerificationCode(request);
        return new MessageResponse("Le enviamos un nuevo código de verificación.");
    }

    @Operation(summary = "Pide el recupero de contraseña",
            description = "Envía un mail con un link para elegir una nueva contraseña. La respuesta es la misma "
                    + "exista o no el email, para no revelar qué emails están registrados.")
    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody EmailRequest request) {
        accountRecoveryService.requestPasswordReset(request);
        return new MessageResponse(
                "Si el email está registrado, le enviamos un enlace para recuperar su contraseña.");
    }

    @Operation(summary = "Cambia la contraseña con el token recibido por mail",
            description = "La contraseña se envía dos veces para verificar que no haya errores de tipeo.")
    @ApiResponse(responseCode = "200", description = "Contraseña actualizada")
    @ApiResponse(responseCode = "400", description = "Las contraseñas no coinciden, o el enlace es inválido o venció")
    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        accountRecoveryService.resetPassword(request);
        return new MessageResponse("Contraseña actualizada. Ya puede iniciar sesión.");
    }
}
