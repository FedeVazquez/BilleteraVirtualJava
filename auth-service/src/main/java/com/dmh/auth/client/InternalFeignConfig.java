package com.dmh.auth.client;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import com.dmh.auth.exception.ResourceNotFoundException;
import com.dmh.auth.exception.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import feign.RequestInterceptor;
import feign.Response;
import feign.codec.ErrorDecoder;

/**
 * Configuracion de los clientes Feign que llaman a endpoints /internal/** de otros microservicios.
 * (Sin @Configuration a proposito: se aplica solo a los clientes que la referencian.)
 */
public class InternalFeignConfig {

    /** Agrega a cada peticion la clave compartida que la identifica como llamada interna. */
    @Bean
    RequestInterceptor internalApiKeyInterceptor(@Value("${app.internal-api-key}") String internalApiKey) {
        return template -> template.header("X-Internal-Api-Key", internalApiKey);
    }

    /**
     * Traduce los errores 404 y 400 del otro servicio a las excepciones propias, conservando su
     * mensaje, para que el cliente final reciba el mismo codigo y texto. El resto de los errores
     * queda como FeignException y termina en un 500.
     */
    @Bean
    ErrorDecoder errorDecoder(ObjectMapper objectMapper) {
        ErrorDecoder defaultDecoder = new ErrorDecoder.Default();
        return (methodKey, response) -> switch (response.status()) {
            case 404 -> new ResourceNotFoundException(readMessage(objectMapper, response, "Recurso inexistente"));
            case 400 -> new ValidationException(readMessage(objectMapper, response, "Datos inválidos"));
            default -> defaultDecoder.decode(methodKey, response);
        };
    }

    private static String readMessage(ObjectMapper objectMapper, Response response, String fallback) {
        if (response.body() == null) {
            return fallback;
        }
        try (InputStream body = response.body().asInputStream()) {
            JsonNode message = objectMapper.readTree(body).path("message");
            return message.isTextual() ? message.asText() : fallback;
        } catch (IOException e) {
            return fallback;
        }
    }
}
