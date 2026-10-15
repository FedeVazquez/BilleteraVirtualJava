package com.dmh.accounts.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Cuenta a la que se le transfirio dinero, con los datos de la ultima transferencia. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecipientResponse(Long accountId, String cvu, String alias, String name, Instant lastTransferDate,
        BigDecimal lastAmount) {
}
