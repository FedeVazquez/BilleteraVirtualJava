package com.dmh.apitests;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.dmh.apitests.support.ApiTest;

/** Sprint 2: registro de tarjetas. */
@Tag("regression")
@DisplayName("Sprint 2 - Tarjetas")
class CardApiTest extends ApiTest {

    @Test
    @DisplayName("TC-120 Sin tarjetas la lista está vacía")
    void listaVacia() {
        TestUser user = loggedIn();
        api(user.token()).get("/accounts/{id}/cards", user.accountId())
                .then().statusCode(200)
                .body("$", empty());
    }

    @Test
    @Tag("smoke")
    @DisplayName("TC-121 Asociar una tarjeta a la cuenta")
    void asociarTarjeta() {
        TestUser user = loggedIn();
        String number = newCardNumber();
        String lastFour = number.substring(number.length() - 4);
        api(user.token()).body(cardJson(number))
                .post("/accounts/{id}/cards", user.accountId())
                .then().statusCode(201)
                .body("lastFour", equalTo(lastFour))
                .body("brand", equalTo("VISA"))
                .body("type", equalTo("DEBIT"));
    }

    @Test
    @Tag("smoke")
    @DisplayName("TC-122 Listar las tarjetas de la cuenta")
    void listarTarjetas() {
        TestUser user = loggedIn();
        addCard(user);
        addCard(user);
        api(user.token()).get("/accounts/{id}/cards", user.accountId())
                .then().statusCode(200)
                .body("$", hasSize(2));
    }

    @Test
    @DisplayName("TC-123 Detalle de una tarjeta")
    void detalleTarjeta() {
        TestUser user = loggedIn();
        long cardId = addCard(user);
        api(user.token()).get("/accounts/{id}/cards/{cardId}", user.accountId(), cardId)
                .then().statusCode(200)
                .body("id", equalTo((int) cardId));
    }

    @Test
    @DisplayName("TC-124 Una tarjeta asociada a otra cuenta no se puede volver a asociar")
    void tarjetaDeOtraCuenta() {
        TestUser first = loggedIn();
        TestUser second = loggedIn();
        String number = newCardNumber();
        api(first.token()).body(cardJson(number)).post("/accounts/{id}/cards", first.accountId()).then().statusCode(201);
        api(second.token()).body(cardJson(number))
                .post("/accounts/{id}/cards", second.accountId())
                .then().statusCode(409)
                .body("message", equalTo("La tarjeta ya está asociada a otra cuenta"));
    }

    @Test
    @DisplayName("TC-125 La misma tarjeta no se asocia dos veces a la misma cuenta")
    void tarjetaRepetida() {
        TestUser user = loggedIn();
        String number = newCardNumber();
        api(user.token()).body(cardJson(number)).post("/accounts/{id}/cards", user.accountId()).then().statusCode(201);
        api(user.token()).body(cardJson(number)).post("/accounts/{id}/cards", user.accountId()).then().statusCode(409);
    }

    @Test
    @DisplayName("TC-126 Número de tarjeta inválido")
    void numeroInvalido() {
        TestUser user = loggedIn();
        api(user.token()).body(cardJson("4509953566233705"))
                .post("/accounts/{id}/cards", user.accountId())
                .then().statusCode(400);
    }

    @Test
    @DisplayName("TC-127 Tarjeta vencida")
    void tarjetaVencida() {
        TestUser user = loggedIn();
        String body = """
                {"number": "%s", "holderName": "Juan Perez", "expiration": "01/20", "cvv": "123", "type": "CREDIT"}
                """.formatted(newCardNumber());
        api(user.token()).body(body).post("/accounts/{id}/cards", user.accountId()).then().statusCode(400);
    }

    @Test
    @DisplayName("TC-128 Datos de tarjeta faltantes")
    void datosFaltantes() {
        TestUser user = loggedIn();
        api(user.token()).body("{}")
                .post("/accounts/{id}/cards", user.accountId())
                .then().statusCode(400)
                .body("details", hasSize(5));
    }

    @Test
    @Tag("smoke")
    @DisplayName("TC-129 Eliminar una tarjeta")
    void eliminarTarjeta() {
        TestUser user = loggedIn();
        long cardId = addCard(user);
        api(user.token()).delete("/accounts/{id}/cards/{cardId}", user.accountId(), cardId).then().statusCode(200);
        api(user.token()).get("/accounts/{id}/cards", user.accountId()).then().body("$", empty());
    }

    @Test
    @DisplayName("TC-130 Eliminar una tarjeta inexistente")
    void eliminarInexistente() {
        TestUser user = loggedIn();
        api(user.token()).delete("/accounts/{id}/cards/999999", user.accountId()).then().statusCode(404);
    }

    @Test
    @DisplayName("TC-131 Las tarjetas de otra cuenta no son accesibles")
    void tarjetasAjenas() {
        TestUser owner = loggedIn();
        TestUser other = loggedIn();
        long cardId = addCard(owner);
        api(other.token()).get("/accounts/{id}/cards", owner.accountId()).then().statusCode(403);
        api(other.token()).get("/accounts/{id}/cards/{cardId}", other.accountId(), cardId).then().statusCode(404);
        api(other.token()).delete("/accounts/{id}/cards/{cardId}", other.accountId(), cardId).then().statusCode(404);
    }

    @Test
    @DisplayName("TC-132 La API no expone el número completo ni el código de seguridad")
    void noExponeDatosSensibles() {
        TestUser user = loggedIn();
        String number = newCardNumber();
        api(user.token()).body(cardJson(number))
                .post("/accounts/{id}/cards", user.accountId())
                .then().statusCode(201)
                .body(not(containsString(number)))
                .body(not(containsString("\"cvv\"")));
    }
}
