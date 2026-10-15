package com.dmh.apitests;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.dmh.apitests.support.ApiTest;

/** Sprint 2: ver y editar el perfil. */
@Tag("regression")
@DisplayName("Sprint 2 - Mi perfil")
class UserProfileApiTest extends ApiTest {

    @Test
    @Tag("smoke")
    @DisplayName("TC-110 El perfil muestra los datos personales, el CVU y el alias")
    void verPerfil() {
        TestUser user = loggedIn();
        api(user.token()).get("/users/{id}", user.id())
                .then().statusCode(200)
                .body("email", equalTo(user.email()))
                .body("dni", equalTo(user.dni()));
        api(user.token()).get("/accounts/{id}", user.accountId())
                .then().statusCode(200)
                .body("cvu", equalTo(user.cvu()))
                .body("alias", equalTo(user.alias()));
    }

    @Test
    @Tag("smoke")
    @DisplayName("TC-111 Editar nombre y teléfono")
    void editarPerfil() {
        TestUser user = loggedIn();
        api(user.token()).body("{\"firstName\": \"Juan Manuel\", \"phone\": \"+5491155556666\"}")
                .patch("/users/{id}", user.id())
                .then().statusCode(200)
                .body("firstName", equalTo("Juan Manuel"))
                .body("lastName", equalTo("Pérez"))
                .body("phone", equalTo("+5491155556666"));
    }

    @Test
    @DisplayName("TC-112 Editar sin enviar ningún dato")
    void editarSinDatos() {
        TestUser user = loggedIn();
        api(user.token()).body("{}").patch("/users/{id}", user.id()).then().statusCode(400);
    }

    @Test
    @DisplayName("TC-113 Editar con datos inválidos")
    void editarDatosInvalidos() {
        TestUser user = loggedIn();
        api(user.token()).body("{\"firstName\": \"   \", \"phone\": \"123\"}")
                .patch("/users/{id}", user.id())
                .then().statusCode(400)
                .body("details", hasSize(2));
    }

    @Test
    @DisplayName("TC-114 El email y el DNI no se modifican")
    void emailYDniNoEditables() {
        TestUser user = loggedIn();
        api(user.token()).body("{\"lastName\": \"Gómez\", \"email\": \"otro@mail.com\", \"dni\": \"99999999\"}")
                .patch("/users/{id}", user.id())
                .then().statusCode(200)
                .body("lastName", equalTo("Gómez"))
                .body("email", equalTo(user.email()))
                .body("dni", equalTo(user.dni()));
    }

    @Test
    @DisplayName("TC-115 No se pueden editar los datos de otro usuario")
    void editarOtroUsuario() {
        TestUser owner = loggedIn();
        TestUser other = loggedIn();
        api(other.token()).body("{\"firstName\": \"Intruso\"}")
                .patch("/users/{id}", owner.id())
                .then().statusCode(403);
    }
}
