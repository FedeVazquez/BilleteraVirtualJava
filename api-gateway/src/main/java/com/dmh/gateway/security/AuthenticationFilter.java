package com.dmh.gateway.security;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

/**
 * Filtro global del gateway: toda peticion a una ruta que no sea publica debe traer un token
 * valido. La validacion se delega en auth-service, que ademas de la firma y el vencimiento revisa
 * que el token no haya sido invalidado por un logout.
 */
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private final SecurityProperties securityProperties;
    private final TokenValidator tokenValidator;
    private final ObjectMapper objectMapper;

    public AuthenticationFilter(SecurityProperties securityProperties, TokenValidator tokenValidator,
            ObjectMapper objectMapper) {
        this.securityProperties = securityProperties;
        this.tokenValidator = tokenValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() {
        // Antes que los filtros de enrutamiento.
        return -1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        // Las peticiones OPTIONS son el "preflight" de CORS del navegador y nunca llevan token.
        if (request.getMethod() == HttpMethod.OPTIONS || securityProperties.isPublic(request.getMethod(), path)) {
            return chain.filter(exchange);
        }
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "Falta el header Authorization: Bearer <token>");
        }
        return tokenValidator.validate(authorization).flatMap(status -> {
            if (status.is2xxSuccessful()) {
                return chain.filter(exchange);
            }
            if (status.value() == HttpStatus.UNAUTHORIZED.value()) {
                return reject(exchange, HttpStatus.UNAUTHORIZED, "Token inválido, vencido o sesión cerrada");
            }
            return reject(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                    "El servicio de autenticación no está disponible. Intente nuevamente más tarde.");
        });
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", exchange.getRequest().getPath().value());

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        } catch (JsonProcessingException e) {
            return response.setComplete();
        }
    }
}
