package com.dmh.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AuthenticationFilterTest {

    @Mock
    private TokenValidator tokenValidator;
    @Mock
    private GatewayFilterChain chain;

    private AuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties(List.of("POST /users/register", "ANY /auth/**"));
        filter = new AuthenticationFilter(properties, tokenValidator, new ObjectMapper());
    }

    @Test
    void unaRutaPublicaPasaSinToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/users/register"));
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        verify(tokenValidator, never()).validate(any());
    }

    @Test
    void unaRutaProtegidaSinTokenDevuelve401SinLlegarAlServicio() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/users/1"));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"status\":401");
        verify(chain, never()).filter(any());
    }

    @Test
    void unaRutaProtegidaConTokenValidoPasa() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/users/1").header(HttpHeaders.AUTHORIZATION, "Bearer token-valido"));
        when(tokenValidator.validate("Bearer token-valido")).thenReturn(Mono.just(HttpStatus.OK));
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void unTokenRechazadoPorAuthServiceDevuelve401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/user/logout").header(HttpHeaders.AUTHORIZATION, "Bearer token-revocado"));
        when(tokenValidator.validate("Bearer token-revocado")).thenReturn(Mono.just(HttpStatus.UNAUTHORIZED));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void siAuthServiceNoRespondeDevuelve503() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/users/1").header(HttpHeaders.AUTHORIZATION, "Bearer token"));
        when(tokenValidator.validate("Bearer token")).thenReturn(Mono.just(HttpStatus.SERVICE_UNAVAILABLE));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(chain, never()).filter(any());
    }
}
