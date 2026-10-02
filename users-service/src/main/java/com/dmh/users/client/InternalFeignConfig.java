package com.dmh.users.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import feign.RequestInterceptor;

/**
 * Configuracion de los clientes Feign que llaman a endpoints /internal/** de otros microservicios:
 * agrega a cada peticion la clave compartida que los identifica como llamadas internas.
 * (Sin @Configuration a proposito: se aplica solo a los clientes que la referencian.)
 */
public class InternalFeignConfig {

    @Bean
    RequestInterceptor internalApiKeyInterceptor(@Value("${app.internal-api-key}") String internalApiKey) {
        return template -> template.header("X-Internal-Api-Key", internalApiKey);
    }
}
