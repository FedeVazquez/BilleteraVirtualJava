package com.dmh.users.dto;

import java.math.BigDecimal;

/** Respuesta de account-service al crear la cuenta digital. */
public record AccountResponse(Long id, Long userId, String cvu, String alias, BigDecimal balance) {
}
