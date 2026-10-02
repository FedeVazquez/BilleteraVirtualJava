package com.dmh.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.test.StepVerifier;

class GatewayErrorHandlerTest {

    private final GatewayErrorHandler handler = new GatewayErrorHandler(new ObjectMapper());

    private MockServerWebExchange handle(Throwable error) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/accounts/user/1"));
        StepVerifier.create(handler.handle(exchange, error)).verifyComplete();
        return exchange;
    }

    @Test
    void unaRutaInexistenteDevuelve404ConElFormatoComun() {
        MockServerWebExchange exchange = handle(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"status\":404", "\"message\":", "\"path\":\"/accounts/user/1\"");
    }

    @Test
    void unServicioSinInstanciasEnEurekaDevuelve503() {
        MockServerWebExchange exchange = handle(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void unServicioQueNoAceptaConexionesDevuelve503() {
        MockServerWebExchange exchange = handle(new RuntimeException(new ConnectException("Connection refused")));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void cualquierOtroErrorDevuelve500SinExponerElDetalleInterno() {
        MockServerWebExchange exchange = handle(new IllegalStateException("detalle interno que no debe salir"));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exchange.getResponse().getBodyAsString().block()).doesNotContain("detalle interno");
    }
}
