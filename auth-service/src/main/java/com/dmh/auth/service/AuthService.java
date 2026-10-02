package com.dmh.auth.service;

import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.dmh.auth.client.UsersClient;
import com.dmh.auth.dto.CredentialsResponse;
import com.dmh.auth.dto.LoginRequest;
import com.dmh.auth.dto.TokenInfoResponse;
import com.dmh.auth.dto.TokenResponse;
import com.dmh.auth.exception.EmailNotVerifiedException;
import com.dmh.auth.exception.UnauthorizedException;
import com.dmh.auth.exception.ValidationException;
import com.dmh.auth.model.RevokedToken;
import com.dmh.auth.repository.RevokedTokenRepository;
import com.dmh.auth.security.JwtService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

@Service
public class AuthService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final UsersClient usersClient;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RevokedTokenRepository revokedTokenRepository;

    public AuthService(UsersClient usersClient, PasswordEncoder passwordEncoder, JwtService jwtService,
            RevokedTokenRepository revokedTokenRepository) {
        this.usersClient = usersClient;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.revokedTokenRepository = revokedTokenRepository;
    }

    /**
     * Valida las credenciales contra users-service y emite el JWT.
     *
     * @throws com.dmh.auth.exception.ResourceNotFoundException si el usuario no existe (404)
     * @throws ValidationException si la contraseña es incorrecta (400)
     * @throws EmailNotVerifiedException si todavia no verifico su email (403)
     */
    public TokenResponse login(LoginRequest request) {
        // Si el usuario no existe, el ErrorDecoder de Feign convierte el 404 en ResourceNotFoundException.
        CredentialsResponse credentials = usersClient.getCredentials(request.email().trim().toLowerCase(Locale.ROOT));
        if (!passwordEncoder.matches(request.password(), credentials.passwordHash())) {
            throw new ValidationException("Contraseña incorrecta");
        }
        if (!credentials.emailVerified()) {
            throw new EmailNotVerifiedException(
                    "Debe verificar su email antes de ingresar. Revise el código que le enviamos por correo.");
        }
        String token = jwtService.generate(credentials.id(), credentials.email(), credentials.roles());
        return TokenResponse.bearer(token, jwtService.getExpirationSeconds());
    }

    /**
     * Invalida el token: guarda su identificador (jti) en la blacklist hasta que venza.
     * Es idempotente: cerrar dos veces la misma sesion no es un error.
     */
    public void logout(String authorizationHeader) {
        Claims claims = parseBearer(authorizationHeader);
        if (!revokedTokenRepository.existsByJti(claims.getId())) {
            try {
                revokedTokenRepository.save(new RevokedToken(claims.getId(), claims.getExpiration().toInstant()));
            } catch (DataIntegrityViolationException e) {
                // otro logout simultaneo del mismo token ya lo guardo
            }
        }
    }

    /**
     * Un token es valido si su firma es correcta, no vencio y no fue invalidado por un logout.
     */
    public TokenInfoResponse validate(String authorizationHeader) {
        Claims claims = parseBearer(authorizationHeader);
        if (revokedTokenRepository.existsByJti(claims.getId())) {
            throw new UnauthorizedException("La sesión fue cerrada. Vuelva a iniciar sesión.");
        }
        List<?> roles = claims.get("roles", List.class);
        return new TokenInfoResponse(Long.valueOf(claims.getSubject()), claims.get("email", String.class),
                roles == null ? List.of() : roles.stream().map(Object::toString).toList());
    }

    private Claims parseBearer(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException("Falta el header Authorization: Bearer <token>");
        }
        try {
            Claims claims = jwtService.parse(authorizationHeader.substring(BEARER_PREFIX.length()).trim());
            if (claims.getId() == null || claims.getSubject() == null || claims.getExpiration() == null) {
                throw new UnauthorizedException("Token inválido");
            }
            return claims;
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("Token inválido o vencido");
        }
    }
}
