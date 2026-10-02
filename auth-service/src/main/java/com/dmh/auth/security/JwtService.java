package com.dmh.auth.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Emite y valida los JWT (firma HS256 con un secreto compartido por todos los microservicios).
 * <p>
 * Claims: sub = id del usuario, email, roles, jti = identificador unico del token (se usa para
 * invalidarlo en el logout), iat y exp. Como iat solo tiene precision de segundos, se agrega
 * iatMs (milisegundos) para poder decidir con exactitud si un token es anterior a un cambio de
 * contraseña.
 */
@Service
public class JwtService {

    public static final String ISSUED_AT_MILLIS = "iatMs";

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(@Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    public String generate(Long userId, String email, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .claim(ISSUED_AT_MILLIS, now.toEpochMilli())
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    /**
     * @throws io.jsonwebtoken.JwtException si la firma no es valida o el token esta vencido
     */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public long getExpirationSeconds() {
        return expiration.toSeconds();
    }
}
