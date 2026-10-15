package com.dmh.accounts.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dmh.accounts.dto.RecipientResponse;
import com.dmh.accounts.dto.TransactionResponse;
import com.dmh.accounts.dto.TransferenceRequest;
import com.dmh.accounts.service.TransferenceService;
import com.dmh.accounts.service.TransferenceService.OperationResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Transferencias")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/accounts/{accountId}/transferences")
public class TransferenceController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String REPLAYED = "Idempotent-Replayed";

    private final TransferenceService transferenceService;

    public TransferenceController(TransferenceService transferenceService) {
        this.transferenceService = transferenceService;
    }

    @Operation(summary = "Ingresar dinero o transferir",
            description = """
                    Mismo endpoint para las dos operaciones que pide la consigna; el cuerpo indica cuál es:
                    - Ingreso desde una tarjeta propia (sprint 3): {"cardId": 1, "amount": 1500}. Responde 201.
                    - Transferencia a otra cuenta (sprint 4): {"destination": "CVU, CBU o alias", "amount": 1500, "description": "opcional"}. Responde 200.
                    Con el header Idempotency-Key, reenviar la misma operación devuelve la original sin repetirla.""")
    @ApiResponse(responseCode = "200", description = "Transferencia realizada: devuelve el movimiento de egreso")
    @ApiResponse(responseCode = "201", description = "Ingreso desde tarjeta registrado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos, cuenta destino inexistente o tarjeta vencida")
    @ApiResponse(responseCode = "403", description = "Sin permisos sobre la cuenta")
    @ApiResponse(responseCode = "404", description = "La tarjeta no existe en esta cuenta")
    @ApiResponse(responseCode = "410", description = "Fondos insuficientes")
    @PostMapping
    public ResponseEntity<TransactionResponse> create(@PathVariable Long accountId,
            @Valid @RequestBody TransferenceRequest request,
            @Parameter(description = "Clave única por operación (por ejemplo un UUID) para evitar duplicados")
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        OperationResult result = request.isDeposit()
                ? transferenceService.deposit(accountId, request, idempotencyKey)
                : transferenceService.transfer(accountId, request, idempotencyKey);
        HttpStatus status = request.isDeposit() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .header(REPLAYED, String.valueOf(result.replayed()))
                .body(result.transaction());
    }

    @Operation(summary = "Últimos destinatarios",
            description = "Las últimas 5 cuentas distintas a las que se transfirió dinero, de la más reciente a la más antigua.")
    @ApiResponse(responseCode = "200", description = "Destinatarios (lista vacía si nunca transfirió)")
    @ApiResponse(responseCode = "403", description = "Sin permisos sobre la cuenta")
    @GetMapping
    public List<RecipientResponse> recentRecipients(@PathVariable Long accountId) {
        return transferenceService.recentRecipients(accountId);
    }
}
