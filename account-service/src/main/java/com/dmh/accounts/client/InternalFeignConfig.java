package com.dmh.accounts.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import feign.RequestInterceptor;

/**
 * Agrega a cada llamada interna la clave compartida entre microservicios.
 * (Sin @Configuration a proposito: se aplica solo a los clientes que la referencian.)
 */
public class InternalFeignConfig {

    @Bean
    RequestInterceptor internalApiKeyInterceptor(@Value("${app.internal-api-key}") String internalApiKey) {
        return template -> template.header("X-Internal-Api-Key", internalApiKey);
    }
}
