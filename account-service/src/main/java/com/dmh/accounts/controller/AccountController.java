package com.dmh.accounts.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dmh.accounts.dto.AccountResponse;
import com.dmh.accounts.service.AccountService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Cuentas")
@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "Cuenta digital (CVU, alias y saldo) de un usuario",
            description = "Solo puede consultarla el dueño de la cuenta o un administrador.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id()")
    @GetMapping("/user/{userId}")
    public AccountResponse getByUser(@PathVariable Long userId) {
        return accountService.getByUserId(userId);
    }
}
