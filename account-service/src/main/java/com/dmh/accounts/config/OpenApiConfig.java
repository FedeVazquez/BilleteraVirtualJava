package com.dmh.accounts.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openApi(@Value("${app.openapi.server-url}") String serverUrl) {
        return new OpenAPI()
                .info(new Info()
                        .title("Digital Money House - Account Service")
                        .version("v1")
                        .description("Cuentas digitales: CVU, alias y saldo."))
                // Las pruebas desde Swagger UI salen por el API Gateway, igual que un cliente real.
                .servers(List.of(new Server().url(serverUrl).description("API Gateway")))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
