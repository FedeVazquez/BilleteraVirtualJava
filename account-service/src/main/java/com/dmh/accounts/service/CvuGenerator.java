package com.dmh.accounts.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * Genera un CVU: 22 digitos numericos aleatorios.
 */
@Component
public class CvuGenerator {

    public static final int CVU_LENGTH = 22;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder cvu = new StringBuilder(CVU_LENGTH);
        for (int i = 0; i < CVU_LENGTH; i++) {
            cvu.append(random.nextInt(10));
        }
        return cvu.toString();
    }
}
