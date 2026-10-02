package com.dmh.accounts.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.dmh.accounts.security.InternalApiKeyFilter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Test de integracion: levanta el contexto completo (seguridad incluida) contra H2 en memoria.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.internal-api-key}")
    private String internalApiKey;

    @Test
    void crearCuentaConClaveInternaDevuelve201ConCvuYAlias() throws Exception {
        mockMvc.perform(post("/internal/accounts")
                .header(InternalApiKeyFilter.HEADER, internalApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": 10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.cvu", matchesPattern("\\d{22}")))
                .andExpect(jsonPath("$.alias", matchesPattern("[a-z]+\\.[a-z]+\\.[a-z]+")))
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void crearCuentaSinClaveInternaDevuelve401() throws Exception {
        mockMvc.perform(post("/internal/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": 11}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void crearCuentaConTokenDeUsuarioDevuelve403() throws Exception {
        mockMvc.perform(post("/internal/accounts")
                .header("Authorization", "Bearer " + token(11L, "ROLE_USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": 11}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void crearCuentaSinUserIdDevuelve400() throws Exception {
        mockMvc.perform(post("/internal/accounts")
                .header(InternalApiKeyFilter.HEADER, internalApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").exists());
    }

    @Test
    void elDuenioPuedeConsultarSuCuenta() throws Exception {
        crearCuenta(20L);

        mockMvc.perform(get("/accounts/user/20").header("Authorization", "Bearer " + token(20L, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(20));
    }

    @Test
    void otroUsuarioNoPuedeConsultarUnaCuentaAjena() throws Exception {
        crearCuenta(30L);

        mockMvc.perform(get("/accounts/user/30").header("Authorization", "Bearer " + token(31L, "ROLE_USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void unAdministradorPuedeConsultarCualquierCuenta() throws Exception {
        crearCuenta(40L);

        mockMvc.perform(get("/accounts/user/40").header("Authorization", "Bearer " + token(1L, "ROLE_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void consultarCuentaSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/accounts/user/20")).andExpect(status().isUnauthorized());
    }

    @Test
    void consultarCuentaInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/accounts/user/999").header("Authorization", "Bearer " + token(999L, "ROLE_USER")))
                .andExpect(status().isNotFound());
    }

    private void crearCuenta(long userId) throws Exception {
        mockMvc.perform(post("/internal/accounts")
                .header(InternalApiKeyFilter.HEADER, internalApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": " + userId + "}"))
                .andExpect(status().isCreated());
    }

    private String token(long userId, String role) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", "user" + userId + "@test.com")
                .claim("roles", List.of(role))
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
