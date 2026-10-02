package com.dmh.gateway.security;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;

/**
 * Le pregunta a auth-service si un token es valido (firma, vencimiento y que no haya sido
 * invalidado por un logout).
 */
@Component
public class TokenValidator {

    private final WebClient webClient;

    public TokenValidator(WebClient.Builder loadBalancedWebClientBuilder) {
        // "auth-service" se resuelve por Eureka gracias al WebClient.Builder @LoadBalanced.
        this.webClient = loadBalancedWebClientBuilder.baseUrl("http://auth-service").build();
    }

    /**
     * @return el codigo HTTP de auth-service: 200 si el token es valido, 401 si no lo es,
     *         o 503 si auth-service no pudo responder.
     */
    public Mono<HttpStatusCode> validate(String authorizationHeader) {
        return webClient.get()
                .uri("/auth/validate")
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .retrieve()
                .toBodilessEntity()
                .map(response -> response.getStatusCode())
                .onErrorResume(WebClientResponseException.class, e -> Mono.just(e.getStatusCode()))
                .onErrorResume(e -> Mono.just(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
