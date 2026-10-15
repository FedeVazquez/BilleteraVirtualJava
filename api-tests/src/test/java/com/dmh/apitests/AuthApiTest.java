package com.dmh.apitests;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.dmh.apitests.support.ApiTest;
import com.dmh.apitests.support.TestConfig;

/** Sprint 1: registro, login, logout y verificacion de email. */
@Tag("regression")
@DisplayName("Sprint 1 - Registro, login y logout")
class AuthApiTest extends ApiTest {

    @Test
    @Tag("smoke")
    @DisplayName("TC-001 Registro exitoso con datos válidos")
    void registroExitoso() {
        String email = uniqueEmail();
        api().body(registerJson(email, uniqueDni()))
                .post("/users/register")
                .then().statusCode(201)
                .body("id", notNullValue())
                .body("accountId", notNullValue())
                .body("email", equalTo(email))
                .body("cvu", matchesPattern("\\d{22}"))
                .body("alias", matchesPattern("[a-z]+\\.[a-z]+\\.[a-z]+"))
                .body("password", nullValue());
    }

    @Test
    @DisplayName("TC-006 No se puede registrar un email ya registrado")
    void emailDuplicado() {
        TestUser user = registered();
        api().body(registerJson(user.email(), uniqueDni()))
                .post("/users/register")
                .then().statusCode(400);
    }

    @Test
    @Tag("smoke")
    @DisplayName("TC-062 Verificación con el código correcto habilita el primer login")
    void verificacionDeEmail() {
        // loggedIn() registra, verifica el email con el codigo del mail y hace login: falla si algun paso falla.
        TestUser user = loggedIn();
        api(user.token()).get("/auth/validate").then().statusCode(200).body("email", equalTo(user.email()));
    }

    @Test
    @DisplayName("TC-061 No se puede hacer login sin verificar el email")
    void loginSinVerificar() {
        TestUser user = registered();
        api().body("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(user.email(), PASSWORD))
                .post("/auth/login")
                .then().statusCode(403);
    }

    @Test
    @Tag("smoke")
    @DisplayName("TC-030 Login exitoso devuelve un token")
    void loginExitoso() {
        TestUser user = loggedIn();
        api().body("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(user.email(), PASSWORD))
                .post("/auth/login")
                .then().statusCode(200)
                .body("token", matchesPattern("[\\w-]+\\.[\\w-]+\\.[\\w-]+"))
                .body("tokenType", equalTo("Bearer"));
    }

    @Test
    @DisplayName("TC-032 Login de un usuario inexistente")
    void loginUsuarioInexistente() {
        api().body("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(uniqueEmail(), PASSWORD))
                .post("/auth/login")
                .then().statusCode(404);
    }

    @Test
    @DisplayName("TC-033 Login con contraseña incorrecta")
    void loginClaveIncorrecta() {
        TestUser user = loggedIn();
        api().body("{\"email\": \"%s\", \"password\": \"OtraClave99\"}".formatted(user.email()))
                .post("/auth/login")
                .then().statusCode(400);
    }

    @Test
    @Tag("smoke")
    @DisplayName("TC-037 Con el token se accede a un recurso protegido")
    void accesoConToken() {
        TestUser user = loggedIn();
        api(user.token()).get("/users/{id}", user.id())
                .then().statusCode(200)
                .body("email", equalTo(user.email()))
                .body("password", nullValue());
    }

    @Test
    @Tag("smoke")
    @DisplayName("TC-039 Recurso protegido sin token")
    void accesoSinToken() {
        api().get("/users/1").then().statusCode(401);
    }

    @Test
    @Tag("smoke")
    @DisplayName("TC-050 Logout con token válido")
    void logout() {
        TestUser user = loggedIn();
        api(user.token()).post("/user/logout").then().statusCode(200);
    }

    @Test
    @Tag("smoke")
    @DisplayName("TC-051 Después del logout el token queda invalidado")
    void tokenInvalidadoTrasLogout() {
        TestUser user = loggedIn();
        api(user.token()).post("/user/logout").then().statusCode(200);
        api(user.token()).get("/users/{id}", user.id()).then().statusCode(401);
    }

    @Test
    @Tag("smoke")
    @DisplayName("TC-080 Los microservicios están registrados en Eureka")
    void serviciosRegistradosEnEureka() {
        given().baseUri(TestConfig.eurekaUrl()).accept("application/json")
                .get("/eureka/apps")
                .then().statusCode(200)
                .body("applications.application.name",
                        hasItems("API-GATEWAY", "AUTH-SERVICE", "USERS-SERVICE", "ACCOUNT-SERVICE"));
    }
}
