package com.dmh.accounts.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dmh.accounts.dto.AccountResponse;
import com.dmh.accounts.dto.CreateAccountRequest;
import com.dmh.accounts.service.AccountService;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;

/**
 * Endpoints de uso interno entre microservicios (los consume users-service por Feign).
 * No se publican en el API Gateway y exigen el header X-Internal-Api-Key.
 */
@Hidden
@RestController
@RequestMapping("/internal/accounts")
public class InternalAccountController {

    private final AccountService accountService;

    public InternalAccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        return accountService.createForUser(request.userId());
    }
}
