package com.dmh.accounts.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.dmh.accounts.support.ApiTestSupport;

/** Sprint 2: dinero disponible, ultimos movimientos, alias y tarjetas. */
class Sprint2ApiTest extends ApiTestSupport {

    // ---------- cuenta y saldo ----------

    @Test
    void elDuenioVeSuCuentaConSaldoCero() throws Exception {
        TestAccount account = newAccount();

        mockMvc.perform(get("/accounts/" + account.id()).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.id()))
                .andExpect(jsonPath("$.cvu").value(account.cvu()))
                .andExpect(jsonPath("$.alias").value(account.alias()))
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void otroUsuarioRecibe403AlConsultarUnaCuentaAjenaOInexistente() throws Exception {
        TestAccount owner = newAccount();
        TestAccount other = newAccount();

        mockMvc.perform(get("/accounts/" + owner.id()).header("Authorization", other.bearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/accounts/999999").header("Authorization", other.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void unAdministradorRecibe404SiLaCuentaNoExiste() throws Exception {
        mockMvc.perform(get("/accounts/999999").header("Authorization", "Bearer " + token(1, "ROLE_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void idDeCuentaNoNumericoDevuelve400() throws Exception {
        TestAccount account = newAccount();

        mockMvc.perform(get("/accounts/abc").header("Authorization", account.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sinMovimientosLaListaDeUltimosMovimientosEstaVacia() throws Exception {
        TestAccount account = newAccount();

        mockMvc.perform(get("/accounts/" + account.id() + "/transactions").header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ---------- alias ----------

    @Test
    void elDuenioPuedeCambiarSuAliasYSeGuardaEnMinusculas() throws Exception {
        TestAccount account = newAccount();
        String alias = "Mi.Alias." + account.id();

        mockMvc.perform(patch("/accounts/" + account.id()).header("Authorization", account.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"alias\": \"" + alias + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").value(alias.toLowerCase()));
    }

    @Test
    void unAliasConFormatoInvalidoDevuelve400() throws Exception {
        TestAccount account = newAccount();

        mockMvc.perform(patch("/accounts/" + account.id()).header("Authorization", account.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"alias\": \"solo.dos\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unAliasYaUsadoPorOtraCuentaDevuelve409() throws Exception {
        TestAccount first = newAccount();
        TestAccount second = newAccount();

        mockMvc.perform(patch("/accounts/" + second.id()).header("Authorization", second.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"alias\": \"" + first.alias() + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void noSePuedeCambiarElAliasDeUnaCuentaAjena() throws Exception {
        TestAccount owner = newAccount();
        TestAccount other = newAccount();

        mockMvc.perform(patch("/accounts/" + owner.id()).header("Authorization", other.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"alias\": \"intento.de.robo\"}"))
                .andExpect(status().isForbidden());
    }

    // ---------- tarjetas ----------

    @Test
    void sinTarjetasLaListaEstaVacia() throws Exception {
        TestAccount account = newAccount();

        mockMvc.perform(get("/accounts/" + account.id() + "/cards").header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void asociarUnaTarjetaDevuelve201SinElNumeroCompletoNiElCodigo() throws Exception {
        TestAccount account = newAccount();
        String number = newCardNumber();

        mockMvc.perform(post("/accounts/" + account.id() + "/cards").header("Authorization", account.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(cardJson(number)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.lastFour").value(number.substring(number.length() - 4)))
                .andExpect(jsonPath("$.number").value("**** **** **** " + number.substring(number.length() - 4)))
                .andExpect(jsonPath("$.brand").value("VISA"))
                .andExpect(jsonPath("$.holderName").value("JUAN PEREZ"))
                .andExpect(jsonPath("$.cvv").doesNotExist());

        mockMvc.perform(get("/accounts/" + account.id() + "/cards").header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void unaTarjetaYaAsociadaAOtraCuentaDevuelve409() throws Exception {
        TestAccount first = newAccount();
        TestAccount second = newAccount();
        String number = newCardNumber();
        mockMvc.perform(post("/accounts/" + first.id() + "/cards").header("Authorization", first.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(cardJson(number)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/accounts/" + second.id() + "/cards").header("Authorization", second.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(cardJson(number)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("La tarjeta ya está asociada a otra cuenta"));
    }

    @Test
    void unNumeroQueNoPasaLuhnDevuelve400() throws Exception {
        TestAccount account = newAccount();

        mockMvc.perform(post("/accounts/" + account.id() + "/cards").header("Authorization", account.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(cardJson("4509953566233705")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El número de tarjeta no es válido"));
    }

    @Test
    void unaTarjetaVencidaDevuelve400() throws Exception {
        TestAccount account = newAccount();
        String body = """
                {"number": "%s", "holderName": "Juan Perez", "expiration": "01/20", "cvv": "123", "type": "CREDIT"}
                """.formatted(newCardNumber());

        mockMvc.perform(post("/accounts/" + account.id() + "/cards").header("Authorization", account.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La tarjeta está vencida"));
    }

    @Test
    void datosDeTarjetaFaltantesDevuelven400ConElDetalle() throws Exception {
        TestAccount account = newAccount();

        mockMvc.perform(post("/accounts/" + account.id() + "/cards").header("Authorization", account.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasSize(5)));
    }

    @Test
    void detalleYEliminacionDeUnaTarjeta() throws Exception {
        TestAccount account = newAccount();
        long cardId = json(mockMvc.perform(post("/accounts/" + account.id() + "/cards")
                .header("Authorization", account.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(cardJson(newCardNumber())))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        String cardUrl = "/accounts/" + account.id() + "/cards/" + cardId;

        mockMvc.perform(get(cardUrl).header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cardId));
        mockMvc.perform(delete(cardUrl).header("Authorization", account.bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(get(cardUrl).header("Authorization", account.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(cardUrl).header("Authorization", account.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void unaTarjetaDeOtraCuentaNoSePuedeVerNiEliminarDesdeLaPropia() throws Exception {
        TestAccount owner = newAccount();
        TestAccount other = newAccount();
        long cardId = json(mockMvc.perform(post("/accounts/" + owner.id() + "/cards")
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(cardJson(newCardNumber())))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();

        mockMvc.perform(get("/accounts/" + other.id() + "/cards/" + cardId).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/accounts/" + other.id() + "/cards/" + cardId).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/accounts/" + owner.id() + "/cards").header("Authorization", other.bearer()))
                .andExpect(status().isForbidden());
    }
}
