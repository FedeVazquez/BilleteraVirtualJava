package com.dmh.accounts.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.dmh.accounts.security.InternalApiKeyFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Base de los tests de integracion de account-service: contexto completo contra H2 y utilidades
 * para crear cuentas y tokens.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestSupport {

    /** Ids de usuario unicos entre tests (la base H2 se comparte en toda la ejecucion). */
    private static final AtomicLong USER_IDS = new AtomicLong(1000);

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;

    @Value("${app.jwt.secret}")
    private String jwtSecret;
    @Value("${app.internal-api-key}")
    private String internalApiKey;

    /** Cuenta de prueba: su id, el id del usuario dueño y un token de ese usuario. */
    protected record TestAccount(long id, long userId, String cvu, String alias, String token) {
        public String bearer() {
            return "Bearer " + token;
        }
    }

    protected TestAccount newAccount() throws Exception {
        long userId = USER_IDS.incrementAndGet();
        MvcResult result = mockMvc.perform(post("/internal/accounts")
                .header(InternalApiKeyFilter.HEADER, internalApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": " + userId + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = json(result);
        return new TestAccount(json.get("id").asLong(), userId, json.get("cvu").asText(), json.get("alias").asText(),
                token(userId, "ROLE_USER"));
    }

    protected String token(long userId, String role) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", "user" + userId + "@test.com")
                .claim("roles", List.of(role))
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** JSON de alta de tarjeta. Los numeros de ejemplo pasan la validacion de Luhn. */
    protected static String cardJson(String number) {
        return """
                {"number": "%s", "holderName": "Juan Perez", "expiration": "12/39", "cvv": "123", "type": "DEBIT"}
                """.formatted(number);
    }

    /** Genera un numero de tarjeta Visa valido (Luhn) y distinto en cada llamada. */
    protected static String newCardNumber() {
        String base = "4" + String.format("%014d", Math.abs(System.nanoTime()) % 100_000_000_000_000L);
        int sum = 0;
        for (int i = 0; i < base.length(); i++) {
            int digit = base.charAt(base.length() - 1 - i) - '0';
            if (i % 2 == 0) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
        }
        return base + ((10 - sum % 10) % 10);
    }
}
