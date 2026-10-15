package com.dmh.apitests;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.dmh.apitests.support.ApiTest;

/** Sprint 2: dinero disponible, ultimos movimientos y alias. */
@Tag("regression")
@DisplayName("Sprint 2 - Cuenta y dinero disponible")
class AccountApiTest extends ApiTest {

    @Test
    @Tag("smoke")
    @DisplayName("TC-100 Ver el dinero disponible de la cuenta propia")
    void dineroDisponible() {
        TestUser user = loggedIn();
        api(user.token()).get("/accounts/{id}", user.accountId())
                .then().statusCode(200)
                .body("id", equalTo((int) user.accountId()))
                .body("cvu", equalTo(user.cvu()))
                .body("alias", equalTo(user.alias()))
                .body("balance", equalTo(0.0f));
    }

    @Test
    @DisplayName("TC-101 No se puede ver la cuenta de otro usuario")
    void cuentaAjena() {
        TestUser owner = loggedIn();
        TestUser other = loggedIn();
        api(other.token()).get("/accounts/{id}", owner.accountId()).then().statusCode(403);
    }

    @Test
    @DisplayName("TC-102 Id de cuenta no numérico")
    void idInvalido() {
        TestUser user = loggedIn();
        api(user.token()).get("/accounts/abc").then().statusCode(400);
    }

    @Test
    @DisplayName("TC-103 Una cuenta nueva no tiene movimientos")
    void sinMovimientos() {
        TestUser user = loggedIn();
        api(user.token()).get("/accounts/{id}/transactions", user.accountId())
                .then().statusCode(200)
                .body("$", empty());
    }

    @Test
    @DisplayName("TC-105 Consultar la cuenta sin token")
    void sinToken() {
        TestUser user = registered();
        api().get("/accounts/{id}", user.accountId()).then().statusCode(401);
    }

    @Test
    @Tag("smoke")
    @DisplayName("TC-116 Modificar el alias de la cuenta")
    void modificarAlias() {
        TestUser user = loggedIn();
        String alias = "nuevo.alias.qa" + user.accountId();
        api(user.token()).body("{\"alias\": \"" + alias + "\"}")
                .patch("/accounts/{id}", user.accountId())
                .then().statusCode(200)
                .body("alias", equalTo(alias));
        api(user.token()).get("/accounts/{id}", user.accountId()).then().body("alias", equalTo(alias));
    }

    @Test
    @DisplayName("TC-117 Alias con formato inválido")
    void aliasInvalido() {
        TestUser user = loggedIn();
        api(user.token()).body("{\"alias\": \"solo.dos\"}")
                .patch("/accounts/{id}", user.accountId())
                .then().statusCode(400)
                .body("details", hasSize(1));
    }

    @Test
    @DisplayName("TC-118 Alias en uso por otra cuenta")
    void aliasEnUso() {
        TestUser first = loggedIn();
        TestUser second = loggedIn();
        api(second.token()).body("{\"alias\": \"" + first.alias() + "\"}")
                .patch("/accounts/{id}", second.accountId())
                .then().statusCode(409);
    }
}
