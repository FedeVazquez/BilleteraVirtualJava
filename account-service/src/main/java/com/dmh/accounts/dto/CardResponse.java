package com.dmh.accounts.dto;

import java.time.Instant;

import com.dmh.accounts.model.Card;
import com.dmh.accounts.model.CardBrand;
import com.dmh.accounts.model.CardType;

/** Datos de una tarjeta para mostrar: el número llega enmascarado. */
public record CardResponse(Long id, Long accountId, String number, String lastFour, CardBrand brand, CardType type,
        String holderName, String expiration, boolean expired, Instant createdAt) {

    public static CardResponse from(Card card) {
        return new CardResponse(card.getId(), card.getAccountId(), "**** **** **** " + card.getLastFour(),
                card.getLastFour(), card.getBrand(), card.getType(), card.getHolderName(),
                String.format("%02d/%02d", card.getExpiration().getMonthValue(), card.getExpiration().getYear() % 100),
                card.isExpired(), card.getCreatedAt());
    }
}
