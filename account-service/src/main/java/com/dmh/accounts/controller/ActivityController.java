package com.dmh.accounts.controller;

import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dmh.accounts.dto.ActivityFilter;
import com.dmh.accounts.dto.TransactionResponse;
import com.dmh.accounts.service.ActivityService;
import com.dmh.accounts.service.ReceiptService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Actividad")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/accounts/{accountId}/activity")
public class ActivityController {

    private final ActivityService activityService;
    private final ReceiptService receiptService;

    public ActivityController(ActivityService activityService, ReceiptService receiptService) {
        this.activityService = activityService;
        this.receiptService = receiptService;
    }

    @Operation(summary = "Toda la actividad de la cuenta",
            description = "Del movimiento más reciente al más antiguo. Se puede filtrar por ingreso/egreso, período y monto.")
    @ApiResponse(responseCode = "200", description = "Movimientos (lista vacía si no hay)")
    @ApiResponse(responseCode = "400", description = "Filtros inválidos")
    @ApiResponse(responseCode = "403", description = "Sin permisos sobre la cuenta")
    @GetMapping
    public List<TransactionResponse> activity(@PathVariable Long accountId, @ParameterObject ActivityFilter filter) {
        return activityService.activity(accountId, filter);
    }

    @Operation(summary = "Detalle de una actividad")
    @ApiResponse(responseCode = "200", description = "Detalle del movimiento")
    @ApiResponse(responseCode = "400", description = "Id inválido")
    @ApiResponse(responseCode = "403", description = "Sin permisos sobre la cuenta")
    @ApiResponse(responseCode = "404", description = "La actividad no existe en esta cuenta")
    @GetMapping("/{transactionId}")
    public TransactionResponse detail(@PathVariable Long accountId, @PathVariable Long transactionId) {
        return activityService.detail(accountId, transactionId);
    }

    @Operation(summary = "Comprobante en PDF de una actividad",
            description = "Descarga un PDF con los mismos datos que el detalle del movimiento.")
    @ApiResponse(responseCode = "200", description = "Archivo PDF",
            content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE))
    @ApiResponse(responseCode = "403", description = "Sin permisos sobre la cuenta")
    @ApiResponse(responseCode = "404", description = "La actividad no existe en esta cuenta")
    @GetMapping(value = "/{transactionId}/receipt", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> receipt(@PathVariable Long accountId, @PathVariable Long transactionId) {
        byte[] pdf = receiptService.receipt(accountId, transactionId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("comprobante-" + transactionId + ".pdf").build().toString())
                .body(pdf);
    }
}
