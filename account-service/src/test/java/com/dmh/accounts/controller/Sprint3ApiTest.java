package com.dmh.accounts.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.dmh.accounts.support.ApiTestSupport;

/** Sprint 3: ingreso de dinero desde tarjeta, actividad y detalle. */
class Sprint3ApiTest extends ApiTestSupport {

    private long addCard(TestAccount account) throws Exception {
        return json(mockMvc.perform(post("/accounts/" + account.id() + "/cards").header("Authorization", account.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(cardJson(newCardNumber())))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    private long deposit(TestAccount account, long cardId, String amount) throws Exception {
        return json(mockMvc.perform(post("/accounts/" + account.id() + "/transferences")
                .header("Authorization", account.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardId\": " + cardId + ", \"amount\": " + amount + "}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    private BigDecimal balance(TestAccount account) throws Exception {
        return new BigDecimal(json(mockMvc.perform(get("/accounts/" + account.id()).header("Authorization", account.bearer()))
                .andExpect(status().isOk()).andReturn()).get("balance").asText());
    }

    // ---------- ingreso de dinero ----------

    @Test
    void ingresarDineroDesdeUnaTarjetaDevuelve201YSumaAlSaldo() throws Exception {
        TestAccount account = newAccount();
        long cardId = addCard(account);

        mockMvc.perform(post("/accounts/" + account.id() + "/transferences").header("Authorization", account.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"cardId\": " + cardId + ", \"amount\": 1500.50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.direction").value("INCOME"))
                .andExpect(jsonPath("$.amount").value(1500.50))
                .andExpect(jsonPath("$.cardLastFour").isString())
                .andExpect(jsonPath("$.date").isString());

        assertThat(balance(account)).isEqualByComparingTo("1500.50");
    }

    @Test
    void ingresarConUnaTarjetaDeOtraCuentaDevuelve404YNoCambiaElSaldo() throws Exception {
        TestAccount owner = newAccount();
        TestAccount other = newAccount();
        long cardOfOwner = addCard(owner);

        mockMvc.perform(post("/accounts/" + other.id() + "/transferences").header("Authorization", other.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"cardId\": " + cardOfOwner + ", \"amount\": 100}"))
                .andExpect(status().isNotFound());
        assertThat(balance(other)).isZero();
    }

    @Test
    void montosInvalidosDevuelven400() throws Exception {
        TestAccount account = newAccount();
        long cardId = addCard(account);
        for (String amount : List.of("0", "-10", "10.555", "1000000.01")) {
            mockMvc.perform(post("/accounts/" + account.id() + "/transferences").header("Authorization", account.bearer())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"cardId\": " + cardId + ", \"amount\": " + amount + "}"))
                    .andExpect(status().isBadRequest());
        }
        assertThat(balance(account)).isZero();
    }

    @Test
    void sinTarjetaNiDestinoOConAmbosDevuelve400() throws Exception {
        TestAccount account = newAccount();
        long cardId = addCard(account);

        mockMvc.perform(post("/accounts/" + account.id() + "/transferences").header("Authorization", account.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 100}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/accounts/" + account.id() + "/transferences").header("Authorization", account.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardId\": " + cardId + ", \"destination\": \"sol.luna.mar\", \"amount\": 100}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noSePuedeIngresarDineroEnUnaCuentaAjena() throws Exception {
        TestAccount owner = newAccount();
        TestAccount other = newAccount();
        long cardId = addCard(owner);

        mockMvc.perform(post("/accounts/" + owner.id() + "/transferences").header("Authorization", other.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"cardId\": " + cardId + ", \"amount\": 100}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ingresosSimultaneosNoPierdenDinero() throws Exception {
        TestAccount account = newAccount();
        long cardId = addCard(account);
        int operations = 12;
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < operations; i++) {
                tasks.add(() -> mockMvc.perform(post("/accounts/" + account.id() + "/transferences")
                        .header("Authorization", account.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardId\": " + cardId + ", \"amount\": 100}"))
                        .andReturn().getResponse().getStatus());
            }
            for (Future<Integer> result : pool.invokeAll(tasks)) {
                assertThat(result.get()).isEqualTo(201);
            }
        } finally {
            pool.shutdown();
        }
        assertThat(balance(account)).isEqualByComparingTo(new BigDecimal(100 * operations));
    }

    // ---------- actividad ----------

    @Test
    void laActividadListaTodoDelMasRecienteAlMasAntiguo() throws Exception {
        TestAccount account = newAccount();
        long cardId = addCard(account);
        long first = deposit(account, cardId, "10");
        long second = deposit(account, cardId, "20");
        long third = deposit(account, cardId, "30");

        mockMvc.perform(get("/accounts/" + account.id() + "/activity").header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(third))
                .andExpect(jsonPath("$[1].id").value(second))
                .andExpect(jsonPath("$[2].id").value(first));
    }

    @Test
    void losUltimosMovimientosSonSolo5() throws Exception {
        TestAccount account = newAccount();
        long cardId = addCard(account);
        long last = 0;
        for (int i = 1; i <= 7; i++) {
            last = deposit(account, cardId, String.valueOf(i));
        }

        mockMvc.perform(get("/accounts/" + account.id() + "/transactions").header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].id").value(last));
        mockMvc.perform(get("/accounts/" + account.id() + "/activity").header("Authorization", account.bearer()))
                .andExpect(jsonPath("$", hasSize(7)));
    }

    @Test
    void filtrosPorMontoTipoYFecha() throws Exception {
        TestAccount account = newAccount();
        long cardId = addCard(account);
        deposit(account, cardId, "500");
        deposit(account, cardId, "3000");
        deposit(account, cardId, "150000");
        String url = "/accounts/" + account.id() + "/activity";

        mockMvc.perform(get(url).param("minAmount", "1000").param("maxAmount", "5000").header("Authorization", account.bearer()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].amount").value(3000));
        mockMvc.perform(get(url).param("minAmount", "100000").header("Authorization", account.bearer()))
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get(url).param("direction", "INCOME").header("Authorization", account.bearer()))
                .andExpect(jsonPath("$", hasSize(3)));
        mockMvc.perform(get(url).param("direction", "EXPENSE").header("Authorization", account.bearer()))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get(url).param("from", "2000-01-01").param("to", "2000-12-31").header("Authorization", account.bearer()))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get(url).param("from", "2000-01-01").header("Authorization", account.bearer()))
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void filtrosInvalidosDevuelven400() throws Exception {
        TestAccount account = newAccount();
        String url = "/accounts/" + account.id() + "/activity";

        mockMvc.perform(get(url).param("from", "2026-10-31").param("to", "2026-10-01").header("Authorization", account.bearer()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(url).param("minAmount", "5000").param("maxAmount", "1000").header("Authorization", account.bearer()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(url).param("direction", "OTRO").header("Authorization", account.bearer()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(url).param("from", "31/10/2026").header("Authorization", account.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void montoYTarjetaEnviadosComoTextoDevuelven400() throws Exception {
        TestAccount account = newAccount();
        long cardId = addCard(account);

        mockMvc.perform(post("/accounts/" + account.id() + "/transferences").header("Authorization", account.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"cardId\": " + cardId + ", \"amount\": \"100\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/accounts/" + account.id() + "/transferences").header("Authorization", account.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"cardId\": \"" + cardId + "\", \"amount\": 100}"))
                .andExpect(status().isBadRequest());
        assertThat(balance(account)).isZero();
    }

    @Test
    void elFiltroDeTipoNoDistingueMayusculasYLosErroresNoExponenClasesInternas() throws Exception {
        TestAccount account = newAccount();
        deposit(account, addCard(account), "10");
        String url = "/accounts/" + account.id() + "/activity";

        mockMvc.perform(get(url).param("direction", "income").header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get(url).param("minAmount", "abc").header("Authorization", account.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("minAmount: tiene un valor inválido"));
    }

    @Test
    void laActividadDeOtraCuentaDevuelve403() throws Exception {
        TestAccount owner = newAccount();
        TestAccount other = newAccount();

        mockMvc.perform(get("/accounts/" + owner.id() + "/activity").header("Authorization", other.bearer()))
                .andExpect(status().isForbidden());
    }

    // ---------- detalle ----------

    @Test
    void detalleDeUnaActividad() throws Exception {
        TestAccount account = newAccount();
        long cardId = addCard(account);
        long transactionId = deposit(account, cardId, "250");

        mockMvc.perform(get("/accounts/" + account.id() + "/activity/" + transactionId).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transactionId))
                .andExpect(jsonPath("$.amount").value(250))
                .andExpect(jsonPath("$.description").isString());
    }

    @Test
    void detalleInexistenteOdeOtraCuentaDevuelve404() throws Exception {
        TestAccount owner = newAccount();
        TestAccount other = newAccount();
        long transactionOfOwner = deposit(owner, addCard(owner), "100");

        mockMvc.perform(get("/accounts/" + other.id() + "/activity/" + transactionOfOwner).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/accounts/" + owner.id() + "/activity/999999").header("Authorization", owner.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/accounts/" + owner.id() + "/activity/" + transactionOfOwner).header("Authorization", other.bearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/accounts/" + owner.id() + "/activity/abc").header("Authorization", owner.bearer()))
                .andExpect(status().isBadRequest());
    }
}
