package com.dmh.accounts.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dmh.accounts.dto.CardRequest;
import com.dmh.accounts.dto.CardResponse;
import com.dmh.accounts.dto.MessageResponse;
import com.dmh.accounts.service.CardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Tarjetas")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/accounts/{accountId}/cards")
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @Operation(summary = "Tarjetas asociadas a la cuenta")
    @ApiResponse(responseCode = "200", description = "Lista de tarjetas (vacía si no tiene)")
    @ApiResponse(responseCode = "403", description = "Sin permisos sobre la cuenta")
    @GetMapping
    public List<CardResponse> list(@PathVariable Long accountId) {
        return cardService.list(accountId);
    }

    @Operation(summary = "Detalle de una tarjeta")
    @ApiResponse(responseCode = "200", description = "Datos de la tarjeta")
    @ApiResponse(responseCode = "404", description = "La tarjeta no existe o no es de esta cuenta")
    @GetMapping("/{cardId}")
    public CardResponse get(@PathVariable Long accountId, @PathVariable Long cardId) {
        return cardService.get(accountId, cardId);
    }

    @Operation(summary = "Asociar una tarjeta a la cuenta",
            description = "Solo se guardan los últimos 4 dígitos; el código de seguridad se valida pero nunca se almacena.")
    @ApiResponse(responseCode = "201", description = "Tarjeta asociada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos, número inválido o tarjeta vencida")
    @ApiResponse(responseCode = "409", description = "La tarjeta ya está asociada a otra cuenta")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CardResponse create(@PathVariable Long accountId, @Valid @RequestBody CardRequest request) {
        return cardService.create(accountId, request);
    }

    @Operation(summary = "Eliminar una tarjeta de la cuenta")
    @ApiResponse(responseCode = "200", description = "Tarjeta eliminada")
    @ApiResponse(responseCode = "404", description = "La tarjeta no existe o no es de esta cuenta")
    @DeleteMapping("/{cardId}")
    public MessageResponse delete(@PathVariable Long accountId, @PathVariable Long cardId) {
        cardService.delete(accountId, cardId);
        return new MessageResponse("Tarjeta eliminada");
    }
}
