package com.dmh.accounts.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dmh.accounts.dto.TransactionResponse;
import com.dmh.accounts.dto.TransferenceRequest;
import com.dmh.accounts.exception.ValidationException;
import com.dmh.accounts.service.TransferenceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Transferencias")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/accounts/{accountId}/transferences")
public class TransferenceController {

    private final TransferenceService transferenceService;

    public TransferenceController(TransferenceService transferenceService) {
        this.transferenceService = transferenceService;
    }

    @Operation(summary = "Ingresar dinero desde una tarjeta propia",
            description = "Cuerpo: {\"cardId\": 1, \"amount\": 1500}. La tarjeta tiene que estar asociada a la cuenta.")
    @ApiResponse(responseCode = "201", description = "Ingreso registrado: devuelve el movimiento")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o tarjeta vencida")
    @ApiResponse(responseCode = "403", description = "Sin permisos sobre la cuenta")
    @ApiResponse(responseCode = "404", description = "La tarjeta no existe en esta cuenta")
    @PostMapping
    public ResponseEntity<TransactionResponse> create(@PathVariable Long accountId,
            @Valid @RequestBody TransferenceRequest request) {
        if (!request.isDeposit()) {
            throw new ValidationException("Las transferencias a otras cuentas todavía no están disponibles");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(transferenceService.deposit(accountId, request));
    }
}
