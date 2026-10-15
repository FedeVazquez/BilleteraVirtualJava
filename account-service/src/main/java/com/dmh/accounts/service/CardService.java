package com.dmh.accounts.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dmh.accounts.dto.CardRequest;
import com.dmh.accounts.dto.CardResponse;
import com.dmh.accounts.exception.ConflictException;
import com.dmh.accounts.exception.ResourceNotFoundException;
import com.dmh.accounts.exception.ValidationException;
import com.dmh.accounts.model.Account;
import com.dmh.accounts.model.Card;
import com.dmh.accounts.model.CardBrand;
import com.dmh.accounts.repository.CardRepository;
import com.dmh.accounts.security.AccountAccess;

@Service
public class CardService {

    static final int MAX_CARDS_PER_ACCOUNT = 10;
    private static final String HMAC = "HmacSHA256";

    private final CardRepository cardRepository;
    private final AccountAccess accountAccess;
    private final SecretKeySpec fingerprintKey;

    public CardService(CardRepository cardRepository, AccountAccess accountAccess,
            @Value("${app.cards.fingerprint-key}") String fingerprintKey) {
        this.cardRepository = cardRepository;
        this.accountAccess = accountAccess;
        this.fingerprintKey = new SecretKeySpec(fingerprintKey.getBytes(StandardCharsets.UTF_8), HMAC);
    }

    @Transactional(readOnly = true)
    public List<CardResponse> list(Long accountId) {
        Account account = accountAccess.requireAccess(accountId);
        return cardRepository.findByAccountIdOrderByCreatedAtDescIdDesc(account.getId()).stream()
                .map(CardResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CardResponse get(Long accountId, Long cardId) {
        return CardResponse.from(findOwnedCard(accountId, cardId));
    }

    /**
     * Asocia una tarjeta a la cuenta. Una misma tarjeta (mismo numero) solo puede estar en una cuenta.
     *
     * @throws ConflictException si la tarjeta ya esta asociada a esta u otra cuenta (409)
     * @throws ValidationException si el numero no es valido, esta vencida o se supero el maximo (400)
     */
    @Transactional
    public CardResponse create(Long accountId, CardRequest request) {
        Account account = accountAccess.requireAccess(accountId);
        if (!passesLuhn(request.number())) {
            throw new ValidationException("El número de tarjeta no es válido");
        }
        YearMonth expiration = parseExpiration(request.expiration());
        if (expiration.isBefore(YearMonth.now())) {
            throw new ValidationException("La tarjeta está vencida");
        }
        String fingerprint = fingerprint(request.number());
        cardRepository.findByNumberFingerprint(fingerprint).ifPresent(existing -> {
            throw new ConflictException(existing.getAccountId().equals(account.getId())
                    ? "La tarjeta ya está asociada a esta cuenta"
                    : "La tarjeta ya está asociada a otra cuenta");
        });
        if (cardRepository.countByAccountId(account.getId()) >= MAX_CARDS_PER_ACCOUNT) {
            throw new ValidationException("Se alcanzó el máximo de " + MAX_CARDS_PER_ACCOUNT + " tarjetas por cuenta");
        }
        String number = request.number();
        Card card = new Card(account.getId(), fingerprint, number.substring(number.length() - 4),
                CardBrand.fromNumber(number), request.type(), request.holderName().trim().toUpperCase(),
                expiration);
        return CardResponse.from(cardRepository.save(card));
    }

    @Transactional
    public void delete(Long accountId, Long cardId) {
        cardRepository.delete(findOwnedCard(accountId, cardId));
    }

    /** Tarjeta de la cuenta, o 404 si no existe o pertenece a otra cuenta. */
    @Transactional(readOnly = true)
    public Card findOwnedCard(Long accountId, Long cardId) {
        Account account = accountAccess.requireAccess(accountId);
        return cardRepository.findByIdAndAccountId(cardId, account.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe la tarjeta " + cardId + " asociada a la cuenta " + accountId));
    }

    /** Algoritmo de Luhn: el digito verificador que tienen todas las tarjetas de pago. */
    static boolean passesLuhn(String number) {
        int sum = 0;
        boolean doubleIt = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = number.charAt(i) - '0';
            if (doubleIt) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    private static YearMonth parseExpiration(String expiration) {
        String[] parts = expiration.split("/");
        return YearMonth.of(2000 + Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
    }

    /**
     * Huella del numero con HMAC-SHA256 y una clave secreta. Un hash simple no alcanza: los numeros
     * de tarjeta tienen pocas combinaciones posibles y se podrian recuperar por fuerza bruta.
     */
    private String fingerprint(String number) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(fingerprintKey);
            return HexFormat.of().formatHex(mac.doFinal(number.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
