package com.dmh.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class CvuGeneratorTest {

    private final CvuGenerator generator = new CvuGenerator();

    @RepeatedTest(20)
    void generaVeintidosDigitosNumericos() {
        assertThat(generator.generate()).matches("\\d{22}");
    }

    @Test
    void generaValoresDistintos() {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            generated.add(generator.generate());
        }
        assertThat(generated).hasSize(100);
    }
}
