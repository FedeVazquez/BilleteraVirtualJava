package com.dmh.apitests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.dmh.apitests.support.ApiTest;

/** Sprint 3: ingreso de dinero desde tarjeta, actividad, detalle y filtros. */
@Tag("regression")
@DisplayName("Sprint 3 - Actividad e ingreso de dinero")
class ActivityApiTest extends ApiTest {

    private static long deposit(TestUser user, long cardId, String amount) {
        return api(user.token()).body("{\"cardId\": " + cardId + ", \"amount\": " + amount + "}")
                .post("/accounts/{id}/transferences", user.accountId())
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private static float balance(TestUser user) {
        return api(user.token()).get("/accounts/{id}", user.accountId()).then().statusCode(200)
                .extract().jsonPath().getFloat("balance");
    }

    // ---------- ingreso de dinero ----------

    @Test
    @Tag("smoke")
    @DisplayName("TC-150 Ingresar dinero desde una tarjeta propia")
    void ingresarDinero() {
        TestUser user = loggedIn();
        long cardId = addCard(user);
        api(user.token()).body("{\"cardId\": " + cardId + ", \"amount\": 1500.50}")
                .post("/accounts/{id}/transferences", user.accountId())
                .then().statusCode(201)
                .body("type", equalTo("DEPOSIT"))
                .body("direction", equalTo("INCOME"))
                .body("amount", equalTo(1500.50f));
        assertThat(balance(user)).isEqualTo(1500.50f);
    }

    @Test
    @DisplayName("TC-151 Ingresar con una tarjeta de otra cuenta")
    void tarjetaDeOtraCuenta() {
        TestUser owner = loggedIn();
        TestUser other = loggedIn();
        long cardOfOwner = addCard(owner);
        api(other.token()).body("{\"cardId\": " + cardOfOwner + ", \"amount\": 100}")
                .post("/accounts/{id}/transferences", other.accountId())
                .then().statusCode(404);
        assertThat(balance(other)).isZero();
    }

    @Test
    @DisplayName("TC-152 Montos inválidos")
    void montosInvalidos() {
        TestUser user = loggedIn();
        long cardId = addCard(user);
        for (String amount : List.of("0", "-10", "10.555", "1000000.01")) {
            api(user.token()).body("{\"cardId\": " + cardId + ", \"amount\": " + amount + "}")
                    .post("/accounts/{id}/transferences", user.accountId())
                    .then().statusCode(400);
        }
        assertThat(balance(user)).isZero();
    }

    @Test
    @DisplayName("TC-153 Monto o tarjeta enviados como texto")
    void tiposIncorrectos() {
        TestUser user = loggedIn();
        long cardId = addCard(user);
        api(user.token()).body("{\"cardId\": " + cardId + ", \"amount\": \"100\"}")
                .post("/accounts/{id}/transferences", user.accountId())
                .then().statusCode(400);
        api(user.token()).body("{\"cardId\": \"" + cardId + "\", \"amount\": 100}")
                .post("/accounts/{id}/transferences", user.accountId())
                .then().statusCode(400);
    }

    @Test
    @DisplayName("TC-154 Ingreso sin tarjeta")
    void sinTarjeta() {
        TestUser user = loggedIn();
        api(user.token()).body("{\"amount\": 100}")
                .post("/accounts/{id}/transferences", user.accountId())
                .then().statusCode(400);
    }

    @Test
    @DisplayName("TC-155 No se puede ingresar dinero en una cuenta ajena")
    void cuentaAjena() {
        TestUser owner = loggedIn();
        TestUser other = loggedIn();
        long cardId = addCard(owner);
        api(other.token()).body("{\"cardId\": " + cardId + ", \"amount\": 100}")
                .post("/accounts/{id}/transferences", owner.accountId())
                .then().statusCode(403);
    }

    @Test
    @DisplayName("TC-156 El ingreso aparece en la actividad y en los últimos movimientos")
    void ingresoVisibleEnActividad() {
        TestUser user = loggedIn();
        long transactionId = deposit(user, addCard(user), "250");
        api(user.token()).get("/accounts/{id}/activity", user.accountId())
                .then().statusCode(200).body("[0].id", equalTo((int) transactionId));
        api(user.token()).get("/accounts/{id}/transactions", user.accountId())
                .then().statusCode(200).body("[0].id", equalTo((int) transactionId));
    }

    // ---------- actividad ----------

    @Test
    @Tag("smoke")
    @DisplayName("TC-140 Toda la actividad, de la más reciente a la más antigua")
    void actividadOrdenada() {
        TestUser user = loggedIn();
        long cardId = addCard(user);
        long first = deposit(user, cardId, "10");
        long second = deposit(user, cardId, "20");
        long third = deposit(user, cardId, "30");
        api(user.token()).get("/accounts/{id}/activity", user.accountId())
                .then().statusCode(200)
                .body("id", equalTo(List.of((int) third, (int) second, (int) first)));
    }

    @Test
    @DisplayName("TC-104 Los últimos movimientos son 5, del más reciente al más antiguo")
    void ultimosCinco() {
        TestUser user = loggedIn();
        long cardId = addCard(user);
        for (int i = 1; i <= 7; i++) {
            deposit(user, cardId, String.valueOf(i));
        }
        api(user.token()).get("/accounts/{id}/transactions", user.accountId())
                .then().statusCode(200)
                .body("amount", equalTo(List.of(7.0f, 6.0f, 5.0f, 4.0f, 3.0f)));
    }

    @Test
    @DisplayName("TC-141 Actividad de una cuenta sin movimientos")
    void actividadVacia() {
        TestUser user = loggedIn();
        api(user.token()).get("/accounts/{id}/activity", user.accountId()).then().statusCode(200).body("$", empty());
    }

    @Test
    @DisplayName("TC-142 Actividad de otra cuenta")
    void actividadAjena() {
        TestUser owner = loggedIn();
        TestUser other = loggedIn();
        api(other.token()).get("/accounts/{id}/activity", owner.accountId()).then().statusCode(403);
    }

    @Test
    @DisplayName("TC-143 Actividad sin token")
    void actividadSinToken() {
        TestUser user = registered();
        api().get("/accounts/{id}/activity", user.accountId()).then().statusCode(401);
    }

    // ---------- detalle ----------

    @Test
    @Tag("smoke")
    @DisplayName("TC-144 Detalle de una actividad")
    void detalle() {
        TestUser user = loggedIn();
        long transactionId = deposit(user, addCard(user), "250");
        api(user.token()).get("/accounts/{id}/activity/{transactionId}", user.accountId(), transactionId)
                .then().statusCode(200)
                .body("id", equalTo((int) transactionId))
                .body("amount", equalTo(250.0f));
    }

    @Test
    @DisplayName("TC-145 Detalle de una actividad inexistente")
    void detalleInexistente() {
        TestUser user = loggedIn();
        api(user.token()).get("/accounts/{id}/activity/999999", user.accountId()).then().statusCode(404);
    }

    @Test
    @DisplayName("TC-146 Detalle de un movimiento de otra cuenta")
    void detalleAjeno() {
        TestUser owner = loggedIn();
        TestUser other = loggedIn();
        long transactionId = deposit(owner, addCard(owner), "100");
        api(other.token()).get("/accounts/{id}/activity/{transactionId}", other.accountId(), transactionId)
                .then().statusCode(404);
        api(other.token()).get("/accounts/{id}/activity/{transactionId}", owner.accountId(), transactionId)
                .then().statusCode(403);
    }

    @Test
    @DisplayName("TC-147 Detalle con id inválido")
    void detalleIdInvalido() {
        TestUser user = loggedIn();
        api(user.token()).get("/accounts/{id}/activity/abc", user.accountId()).then().statusCode(400);
    }

    // ---------- filtros (opcional) ----------

    @Test
    @DisplayName("TC-160 Filtrar la actividad por rango de monto")
    void filtroPorMonto() {
        TestUser user = loggedIn();
        long cardId = addCard(user);
        deposit(user, cardId, "500");
        deposit(user, cardId, "3000");
        deposit(user, cardId, "150000");
        api(user.token()).queryParam("minAmount", 1000).queryParam("maxAmount", 5000)
                .get("/accounts/{id}/activity", user.accountId())
                .then().statusCode(200).body("amount", equalTo(List.of(3000.0f)));
        api(user.token()).queryParam("minAmount", 100000)
                .get("/accounts/{id}/activity", user.accountId())
                .then().statusCode(200).body("$", hasSize(1));
    }

    @Test
    @DisplayName("TC-161 Filtrar la actividad por ingreso o egreso")
    void filtroPorTipo() {
        TestUser user = loggedIn();
        deposit(user, addCard(user), "100");
        api(user.token()).queryParam("direction", "income").get("/accounts/{id}/activity", user.accountId())
                .then().statusCode(200).body("$", hasSize(1));
        api(user.token()).queryParam("direction", "EXPENSE").get("/accounts/{id}/activity", user.accountId())
                .then().statusCode(200).body("$", empty());
    }

    @Test
    @DisplayName("TC-162 Filtrar la actividad por período")
    void filtroPorPeriodo() {
        TestUser user = loggedIn();
        deposit(user, addCard(user), "100");
        api(user.token()).queryParam("from", "2000-01-01").queryParam("to", "2000-12-31")
                .get("/accounts/{id}/activity", user.accountId())
                .then().statusCode(200).body("$", empty());
        api(user.token()).queryParam("from", "2000-01-01")
                .get("/accounts/{id}/activity", user.accountId())
                .then().statusCode(200).body("$", hasSize(1));
    }

    @Test
    @DisplayName("TC-163 Filtros inválidos")
    void filtrosInvalidos() {
        TestUser user = loggedIn();
        api(user.token()).queryParam("from", "2026-10-31").queryParam("to", "2026-10-01")
                .get("/accounts/{id}/activity", user.accountId()).then().statusCode(400);
        api(user.token()).queryParam("minAmount", "abc")
                .get("/accounts/{id}/activity", user.accountId())
                .then().statusCode(400)
                .body(not(containsString("java.")));
    }
}
