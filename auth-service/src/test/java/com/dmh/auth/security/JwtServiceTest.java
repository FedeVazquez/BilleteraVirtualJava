package com.dmh.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

class JwtServiceTest {

    private static final String SECRET = "test-secret-de-al-menos-32-bytes-0123456789";

    private final JwtService jwtService = new JwtService(SECRET, 60);

    @Test
    void elTokenLlevaElUsuarioElEmailLosRolesYUnIdentificadorUnico() {
        String token = jwtService.generate(7L, "juan@mail.com", List.of("ROLE_USER"));

        Claims claims = jwtService.parse(token);

        assertThat(claims.getSubject()).isEqualTo("7");
        assertThat(claims.get("email", String.class)).isEqualTo("juan@mail.com");
        assertThat(claims.get("roles", List.class)).containsExactly("ROLE_USER");
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void dosTokensDelMismoUsuarioTienenIdentificadoresDistintos() {
        String first = jwtService.generate(7L, "juan@mail.com", List.of("ROLE_USER"));
        String second = jwtService.generate(7L, "juan@mail.com", List.of("ROLE_USER"));

        assertThat(jwtService.parse(first).getId()).isNotEqualTo(jwtService.parse(second).getId());
    }

    @Test
    void rechazaUnTokenFirmadoConOtroSecreto() {
        JwtService other = new JwtService("otro-secreto-distinto-de-al-menos-32-bytes!!", 60);
        String token = other.generate(7L, "juan@mail.com", List.of("ROLE_USER"));

        assertThatThrownBy(() -> jwtService.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rechazaUnTokenVencido() {
        JwtService alreadyExpired = new JwtService(SECRET, -1);
        String token = alreadyExpired.generate(7L, "juan@mail.com", List.of("ROLE_USER"));

        assertThatThrownBy(() -> jwtService.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rechazaUnTokenAdulterado() {
        String token = jwtService.generate(7L, "juan@mail.com", List.of("ROLE_USER"));
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        assertThatThrownBy(() -> jwtService.parse(tampered)).isInstanceOf(JwtException.class);
    }
}
