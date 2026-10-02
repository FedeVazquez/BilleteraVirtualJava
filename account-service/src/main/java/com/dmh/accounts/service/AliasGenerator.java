package com.dmh.accounts.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Genera un alias con 3 palabras distintas, elegidas al azar de un archivo TXT, separadas por punto.
 */
@Component
public class AliasGenerator {

    public static final int WORDS_PER_ALIAS = 3;

    private final List<String> words;
    private final SecureRandom random = new SecureRandom();

    public AliasGenerator(@Value("${app.alias.words-file}") Resource wordsFile) {
        this.words = loadWords(wordsFile);
        if (words.size() < WORDS_PER_ALIAS) {
            throw new IllegalStateException(
                    "El archivo de palabras para alias debe tener al menos " + WORDS_PER_ALIAS + " palabras");
        }
    }

    public String generate() {
        List<String> pool = new ArrayList<>(words);
        List<String> chosen = new ArrayList<>(WORDS_PER_ALIAS);
        for (int i = 0; i < WORDS_PER_ALIAS; i++) {
            chosen.add(pool.remove(random.nextInt(pool.size())));
        }
        return String.join(".", chosen);
    }

    private static List<String> loadWords(Resource wordsFile) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(wordsFile.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(String::toLowerCase)
                    .distinct()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo de palabras para alias", e);
        }
    }
}
