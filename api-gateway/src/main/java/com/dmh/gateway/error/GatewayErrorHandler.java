package com.dmh.gateway.error;

import java.net.ConnectException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

/**
 * Errores que se producen en el propio gateway (ruta inexistente, servicio destino caido o sin
 * instancias en Eureka...). Devuelve el mismo formato JSON de error que usan los microservicios.
 */
@Component
@Order(-2) // antes que el manejador por defecto de Spring Boot
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);

    private final ObjectMapper objectMapper;

    public GatewayErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }
        HttpStatus status = resolveStatus(ex);
        String path = exchange.getRequest().getPath().value();
        if (status.is5xxServerError()) {
            log.warn("Error {} en {} {}: {}", status.value(), exchange.getRequest().getMethod(), path, ex.toString());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", messageFor(status));
        body.put("path", path);

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            return response.writeWith(Mono.just(response.bufferFactory().wrap(objectMapper.writeValueAsBytes(body))));
        } catch (JsonProcessingException e) {
            return response.setComplete();
        }
    }

    private static HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof ResponseStatusException statusException) {
            HttpStatus status = HttpStatus.resolve(statusException.getStatusCode().value());
            return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        }
        // El servicio destino esta registrado pero no responde (caido o reiniciandose).
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof TimeoutException) {
                return HttpStatus.SERVICE_UNAVAILABLE;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static String messageFor(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "El recurso solicitado no existe";
            case SERVICE_UNAVAILABLE -> "El servicio no está disponible. Intente nuevamente más tarde.";
            case INTERNAL_SERVER_ERROR -> "Ocurrió un error interno. Intente nuevamente más tarde.";
            default -> status.getReasonPhrase();
        };
    }
}
