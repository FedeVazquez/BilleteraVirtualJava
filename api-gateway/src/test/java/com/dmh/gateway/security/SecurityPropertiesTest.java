package com.dmh.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class SecurityPropertiesTest {

    private final SecurityProperties properties = new SecurityProperties(
            List.of("POST /users/register", "ANY /auth/**", "GET /api-docs/**"));

    @Test
    void elRegistroEsPublicoSoloPorPost() {
        assertThat(properties.isPublic(HttpMethod.POST, "/users/register")).isTrue();
        assertThat(properties.isPublic(HttpMethod.GET, "/users/register")).isFalse();
    }

    @Test
    void todoLoDeAuthEsPublicoConCualquierMetodo() {
        assertThat(properties.isPublic(HttpMethod.POST, "/auth/login")).isTrue();
        assertThat(properties.isPublic(HttpMethod.GET, "/auth/reset-password.html")).isTrue();
    }

    @Test
    void elRestoDeLasRutasExigeToken() {
        assertThat(properties.isPublic(HttpMethod.GET, "/users/1")).isFalse();
        assertThat(properties.isPublic(HttpMethod.POST, "/user/logout")).isFalse();
        assertThat(properties.isPublic(HttpMethod.GET, "/accounts/user/1")).isFalse();
    }

    @Test
    void sinConfiguracionNingunaRutaEsPublica() {
        assertThat(new SecurityProperties(null).isPublic(HttpMethod.POST, "/auth/login")).isFalse();
    }
}
