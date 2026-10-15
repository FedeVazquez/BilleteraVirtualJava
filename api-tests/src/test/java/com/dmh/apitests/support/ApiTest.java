package com.dmh.apitests.support;

import static io.restassured.RestAssured.given;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/**
 * Base de los tests de API: configura RestAssured para entrar por el gateway y ofrece utilidades para
 * crear usuarios de prueba con datos unicos.
 */
public abstract class ApiTest {

    public static final String PASSWORD = "Clave1234";

    /** Prefijo aleatorio por ejecucion: los datos nunca chocan con los de una corrida anterior. */
    private static final int RUN = ThreadLocalRandom.current().nextInt(100, 999);
    private static final AtomicInteger COUNTER = new AtomicInteger();

    @BeforeAll
    static void configureRestAssured() {
        RestAssured.baseURI = TestConfig.baseUrl();
        // Si una verificacion falla, se imprime la peticion y la respuesta completas.
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    protected static RequestSpecification api() {
        return given().contentType(ContentType.JSON).accept(ContentType.JSON);
    }

    protected static RequestSpecification api(String token) {
        return api().header("Authorization", "Bearer " + token);
    }

    /** Usuario de prueba: datos de registro, ids y (si se logueo) su token. */
    public record TestUser(long id, long accountId, String email, String dni, String cvu, String alias, String token) {
    }

    protected static String uniqueEmail() {
        return "qa" + RUN + "n" + COUNTER.incrementAndGet() + "." + System.nanoTime() % 100_000 + "@mail.com";
    }

    protected static String uniqueDni() {
        // 8 digitos que cambian en cada llamada y entre ejecuciones (los datos persisten en MySQL).
        return String.format("%08d", (System.currentTimeMillis() / 10 + COUNTER.incrementAndGet()) % 100_000_000);
    }

    protected static String registerJson(String email, String dni) {
        return """
                {"firstName": "Juan", "lastName": "Pérez", "dni": "%s", "email": "%s",
                 "phone": "+5491122334455", "password": "%s"}
                """.formatted(dni, email, PASSWORD);
    }

    /** Registra un usuario nuevo (sin verificar el email). */
    protected static TestUser registered() {
        String email = uniqueEmail();
        String dni = uniqueDni();
        var json = api().body(registerJson(email, dni))
                .post("/users/register")
                .then().statusCode(201)
                .extract().jsonPath();
        return new TestUser(json.getLong("id"), json.getLong("accountId"), email, dni, json.getString("cvu"),
                json.getString("alias"), null);
    }

    /** Registra un usuario, verifica su email con el codigo del mail e inicia sesion. */
    protected static TestUser loggedIn() {
        TestUser user = registered();
        api().body("{\"email\": \"%s\", \"code\": \"%s\"}".formatted(user.email(), Mailbox.verificationCode(user.email())))
                .post("/auth/verify-email")
                .then().statusCode(200);
        return withToken(user, login(user.email(), PASSWORD));
    }

    protected static String login(String email, String password) {
        return api().body("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password))
                .post("/auth/login")
                .then().statusCode(200)
                .extract().path("token");
    }

    protected static TestUser withToken(TestUser user, String token) {
        return new TestUser(user.id(), user.accountId(), user.email(), user.dni(), user.cvu(), user.alias(), token);
    }

    /** Numero de tarjeta Visa valido (pasa Luhn) y distinto en cada llamada. */
    protected static String newCardNumber() {
        String base = "4" + String.format("%014d", Math.abs(System.nanoTime() + COUNTER.incrementAndGet()) % 100_000_000_000_000L);
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

    protected static String cardJson(String number) {
        return """
                {"number": "%s", "holderName": "Juan Perez", "expiration": "12/39", "cvv": "123", "type": "DEBIT"}
                """.formatted(number);
    }

    /** Asocia una tarjeta nueva a la cuenta del usuario y devuelve su id. */
    protected static long addCard(TestUser user) {
        return api(user.token()).body(cardJson(newCardNumber()))
                .post("/accounts/{id}/cards", user.accountId())
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }
}
