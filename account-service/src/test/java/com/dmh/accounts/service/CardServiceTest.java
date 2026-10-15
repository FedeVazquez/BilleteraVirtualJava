package com.dmh.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.dmh.accounts.model.CardBrand;

class CardServiceTest {

    @ParameterizedTest
    @ValueSource(strings = { "4509953566233704", "5031755734530604", "371449635398431", "4111111111111111" })
    void numerosValidosPasanLuhn(String number) {
        assertThat(CardService.passesLuhn(number)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "4509953566233705", "1234567812345678", "4111111111111112" })
    void numerosConDigitoVerificadorIncorrectoNoPasanLuhn(String number) {
        assertThat(CardService.passesLuhn(number)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({ "4509953566233704,VISA", "5555555555554444,MASTERCARD", "2223000048400011,MASTERCARD",
            "371449635398431,AMEX", "6011000990139424,OTHER" })
    void laMarcaSeDeduceDeLosPrimerosDigitos(String number, CardBrand brand) {
        assertThat(CardBrand.fromNumber(number)).isEqualTo(brand);
    }
}
