package com.dmh.accounts.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dmh.accounts.dto.AccountResponse;
import com.dmh.accounts.dto.TransactionResponse;
import com.dmh.accounts.dto.UpdateAccountRequest;
import com.dmh.accounts.service.AccountService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Cuentas")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "Datos de la cuenta y dinero disponible",
            description = "Solo puede consultarla su dueño o un administrador.")
    @ApiResponse(responseCode = "200", description = "Cuenta con su saldo")
    @ApiResponse(responseCode = "400", description = "Id inválido")
    @ApiResponse(responseCode = "403", description = "Sin permisos sobre la cuenta")
    @GetMapping("/{id}")
    public AccountResponse getById(@PathVariable Long id) {
        return accountService.getById(id);
    }

    @Operation(summary = "Modificar el alias de la cuenta")
    @ApiResponse(responseCode = "200", description = "Alias actualizado")
    @ApiResponse(responseCode = "400", description = "Formato de alias inválido")
    @ApiResponse(responseCode = "403", description = "Sin permisos sobre la cuenta")
    @ApiResponse(responseCode = "409", description = "El alias ya está en uso")
    @PatchMapping("/{id}")
    public AccountResponse update(@PathVariable Long id, @Valid @RequestBody UpdateAccountRequest request) {
        return accountService.updateAlias(id, request.alias());
    }

    @Operation(summary = "Últimos 5 movimientos", description = "Del más reciente al más antiguo.")
    @ApiResponse(responseCode = "200", description = "Lista de movimientos (vacía si no hay)")
    @ApiResponse(responseCode = "403", description = "Sin permisos sobre la cuenta")
    @GetMapping("/{id}/transactions")
    public List<TransactionResponse> lastTransactions(@PathVariable Long id) {
        return accountService.lastTransactions(id);
    }

    @Operation(summary = "Cuenta digital de un usuario",
            description = "Permite al cliente conocer el id de su cuenta a partir del id de usuario del token.")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id()")
    @GetMapping("/user/{userId}")
    public AccountResponse getByUser(@PathVariable Long userId) {
        return accountService.getByUserId(userId);
    }
}
