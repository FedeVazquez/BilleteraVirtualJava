package com.dmh.users.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.dmh.users.dto.AccountResponse;
import com.dmh.users.dto.CreateAccountRequest;

/**
 * Cliente Feign de account-service. "account-service" es el nombre con el que el servicio se registra
 * en Eureka: Feign resuelve la direccion real consultando el registro y balancea entre instancias.
 */
@FeignClient(name = "account-service", configuration = InternalFeignConfig.class)
public interface AccountClient {

    @PostMapping("/internal/accounts")
    AccountResponse create(@RequestBody CreateAccountRequest request);
}
