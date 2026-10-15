package com.dmh.accounts.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.ResultActions;

import com.dmh.accounts.support.ApiTestSupport;

/** Sprint 4: transferencias entre cuentas, ultimos destinatarios y comprobante PDF. */
class Sprint4ApiTest extends ApiTestSupport {

    private TestAccount accountWithBalance(String amount) throws Exception {
        TestAccount account = newAccount();
        long cardId = json(mockMvc.perform(post("/accounts/" + account.id() + "/cards").header("Authorization", account.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(cardJson(newCardNumber())))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        mockMvc.perform(post("/accounts/" + account.id() + "/transferences").header("Authorization", account.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"cardId\": " + cardId + ", \"amount\": " + amount + "}"))
                .andExpect(status().isCreated());
        return account;
    }

    private ResultActions transfer(TestAccount from, String destination, String amount) throws Exception {
        return mockMvc.perform(post("/accounts/" + from.id() + "/transferences").header("Authorization", from.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"destination\": \"" + destination + "\", \"amount\": " + amount + ", \"description\": \"Alquiler\"}"));
    }

    private BigDecimal balance(TestAccount account) throws Exception {
        return new BigDecimal(json(mockMvc.perform(get("/accounts/" + account.id()).header("Authorization", account.bearer()))
                .andReturn()).get("balance").asText());
    }

    // ---------- transferencias ----------

    @Test
    void transferirPorAliasDevuelve200YMueveElDineroEntreLasDosCuentas() throws Exception {
        TestAccount source = accountWithBalance("1000");
        TestAccount target = newAccount();

        transfer(source, target.alias().toUpperCase(), "250.50")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.direction").value("EXPENSE"))
                .andExpect(jsonPath("$.amount").value(250.50))
                .andExpect(jsonPath("$.description").value("Alquiler"))
                .andExpect(jsonPath("$.counterpart.cvu").value(target.cvu()))
                .andExpect(jsonPath("$.counterpart.name").value("Titular N" + target.userId()));

        assertThat(balance(source)).isEqualByComparingTo("749.50");
        assertThat(balance(target)).isEqualByComparingTo("250.50");
        mockMvc.perform(get("/accounts/" + target.id() + "/activity").header("Authorization", target.bearer()))
                .andExpect(jsonPath("$[0].type").value("TRANSFER_IN"))
                .andExpect(jsonPath("$[0].counterpart.cvu").value(source.cvu()))
                .andExpect(jsonPath("$[0].counterpart.name").value("Titular N" + source.userId()));
    }

    @Test
    void transferirPorCvuDevuelve200() throws Exception {
        TestAccount source = accountWithBalance("100");
        TestAccount target = newAccount();

        transfer(source, target.cvu(), "100").andExpect(status().isOk());

        assertThat(balance(source)).isZero();
        assertThat(balance(target)).isEqualByComparingTo("100");
    }

    @Test
    void sinFondosSuficientesDevuelve410YNoMueveDinero() throws Exception {
        TestAccount source = accountWithBalance("100");
        TestAccount target = newAccount();

        transfer(source, target.alias(), "100.01")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410));

        assertThat(balance(source)).isEqualByComparingTo("100");
        assertThat(balance(target)).isZero();
    }

    @Test
    void unaCuentaDestinoInexistenteDevuelve400() throws Exception {
        TestAccount source = accountWithBalance("100");

        transfer(source, "no.existe.nadie", "10").andExpect(status().isBadRequest());
        transfer(source, "0000000000000000000000", "10").andExpect(status().isBadRequest());
        assertThat(balance(source)).isEqualByComparingTo("100");
    }

    @Test
    void transferirALaMismaCuentaDevuelve400() throws Exception {
        TestAccount source = accountWithBalance("100");

        transfer(source, source.alias(), "10").andExpect(status().isBadRequest());
    }

    @Test
    void noSePuedeTransferirDesdeUnaCuentaAjena() throws Exception {
        TestAccount owner = accountWithBalance("100");
        TestAccount thief = newAccount();

        mockMvc.perform(post("/accounts/" + owner.id() + "/transferences").header("Authorization", thief.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"destination\": \"" + thief.alias() + "\", \"amount\": 50}"))
                .andExpect(status().isForbidden());
        assertThat(balance(owner)).isEqualByComparingTo("100");
    }

    @Test
    void siUsersServiceNoRespondeLaTransferenciaSeHaceIgualSinElNombre() throws Exception {
        TestAccount source = accountWithBalance("100");
        TestAccount target = newAccount();
        when(usersClient.summary(anyLong())).thenThrow(new IllegalStateException("users-service caido"));

        transfer(source, target.alias(), "10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counterpart.name").doesNotExist());
    }

    @Test
    void conLaMismaClaveDeIdempotenciaLaTransferenciaSeHaceUnaSolaVez() throws Exception {
        TestAccount source = accountWithBalance("1000");
        TestAccount target = newAccount();
        String body = "{\"destination\": \"" + target.alias() + "\", \"amount\": 100}";

        long first = json(mockMvc.perform(post("/accounts/" + source.id() + "/transferences").header("Authorization", source.bearer())
                .header("Idempotency-Key", "pago-123").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andReturn()).get("id").asLong();
        mockMvc.perform(post("/accounts/" + source.id() + "/transferences").header("Authorization", source.bearer())
                .header("Idempotency-Key", "pago-123").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(first));

        assertThat(balance(source)).isEqualByComparingTo("900");
        assertThat(balance(target)).isEqualByComparingTo("100");
    }

    @Test
    void reutilizarLaClaveConOtrosDatosDevuelve409() throws Exception {
        TestAccount source = accountWithBalance("1000");
        TestAccount target = newAccount();
        for (String amount : new String[] { "100", "1" }) {
            mockMvc.perform(post("/accounts/" + source.id() + "/transferences").header("Authorization", source.bearer())
                    .header("Idempotency-Key", "clave-repetida").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"destination\": \"" + target.alias() + "\", \"amount\": " + amount + "}"))
                    .andExpect(amount.equals("100") ? status().isOk() : status().isConflict());
        }
        assertThat(balance(source)).isEqualByComparingTo("900");
    }

    @Test
    void unCvuConEspaciosOGuionesSeReconoce() throws Exception {
        TestAccount source = accountWithBalance("100");
        TestAccount target = newAccount();
        String spaced = target.cvu().substring(0, 8) + " " + target.cvu().substring(8, 15) + "-" + target.cvu().substring(15);

        transfer(source, spaced, "10").andExpect(status().isOk());
        assertThat(balance(target)).isEqualByComparingTo("10");
    }

    @Test
    void transferenciasCruzadasSimultaneasNoSeBloqueanNiPierdenDinero() throws Exception {
        TestAccount a = accountWithBalance("1000");
        TestAccount b = accountWithBalance("1000");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                tasks.add(() -> transfer(a, b.alias(), "10").andReturn().getResponse().getStatus());
                tasks.add(() -> transfer(b, a.alias(), "5").andReturn().getResponse().getStatus());
            }
            for (Future<Integer> result : pool.invokeAll(tasks)) {
                assertThat(result.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdown();
        }
        assertThat(balance(a)).isEqualByComparingTo("950");
        assertThat(balance(b)).isEqualByComparingTo("1050");
    }

    @Test
    void elIngresoTambienRespetaLaClaveDeIdempotencia() throws Exception {
        TestAccount account = newAccount();
        long cardId = json(mockMvc.perform(post("/accounts/" + account.id() + "/cards").header("Authorization", account.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(cardJson(newCardNumber())))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/accounts/" + account.id() + "/transferences").header("Authorization", account.bearer())
                    .header("Idempotency-Key", "carga-1").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"cardId\": " + cardId + ", \"amount\": 300}"))
                    .andExpect(status().isCreated());
        }
        assertThat(balance(account)).isEqualByComparingTo("300");
    }

    // ---------- ultimos destinatarios ----------

    @Test
    void losUltimosDestinatariosSonLas5CuentasDistintasMasRecientes() throws Exception {
        TestAccount source = accountWithBalance("1000");
        List<TestAccount> targets = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            targets.add(newAccount());
        }
        for (TestAccount target : targets) {
            transfer(source, target.alias(), "1").andExpect(status().isOk());
        }
        transfer(source, targets.get(2).alias(), "7").andExpect(status().isOk());

        mockMvc.perform(get("/accounts/" + source.id() + "/transferences").header("Authorization", source.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].accountId").value(targets.get(2).id()))
                .andExpect(jsonPath("$[0].lastAmount").value(7))
                .andExpect(jsonPath("$[1].accountId").value(targets.get(5).id()))
                .andExpect(jsonPath("$[4].accountId").value(targets.get(1).id()));
    }

    @Test
    void sinTransferenciasLaListaDeDestinatariosEstaVaciaYUnaCuentaAjenaDevuelve403() throws Exception {
        TestAccount account = newAccount();
        TestAccount other = newAccount();

        mockMvc.perform(get("/accounts/" + account.id() + "/transferences").header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/accounts/" + account.id() + "/transferences").header("Authorization", other.bearer()))
                .andExpect(status().isForbidden());
    }

    // ---------- comprobante ----------

    @Test
    void elComprobanteEsUnPdfDescargable() throws Exception {
        TestAccount source = accountWithBalance("100");
        TestAccount target = newAccount();
        long transactionId = json(transfer(source, target.alias(), "40").andReturn()).get("id").asLong();

        byte[] pdf = mockMvc.perform(get("/accounts/" + source.id() + "/activity/" + transactionId + "/receipt")
                .header("Authorization", source.bearer()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"comprobante-" + transactionId + ".pdf\""))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void elComprobanteDeUnMovimientoAjenoNoSePuedeDescargar() throws Exception {
        TestAccount source = accountWithBalance("100");
        TestAccount other = newAccount();
        long transactionId = json(transfer(source, other.alias(), "40").andReturn()).get("id").asLong();

        mockMvc.perform(get("/accounts/" + other.id() + "/activity/" + transactionId + "/receipt").header("Authorization", other.bearer())
                .accept(MediaType.APPLICATION_PDF))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        mockMvc.perform(get("/accounts/" + source.id() + "/activity/" + transactionId + "/receipt").header("Authorization", other.bearer()))
                .andExpect(status().isForbidden());
    }
}
