package com.dmh.apitests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.dmh.apitests.support.ApiTest;

import io.restassured.response.ValidatableResponse;

/** Sprint 4: transferencias, ultimos destinatarios y comprobante. */
@Tag("regression")
@DisplayName("Sprint 4 - Transferencias")
class TransferApiTest extends ApiTest {

    /** Usuario logueado con saldo cargado desde una tarjeta. */
    private static TestUser withBalance(String amount) {
        TestUser user = loggedIn();
        long cardId = addCard(user);
        api(user.token()).body("{\"cardId\": " + cardId + ", \"amount\": " + amount + "}")
                .post("/accounts/{id}/transferences", user.accountId())
                .then().statusCode(201);
        return user;
    }

    private static ValidatableResponse transfer(TestUser from, String destination, String amount) {
        return api(from.token())
                .body("{\"destination\": \"" + destination + "\", \"amount\": " + amount + ", \"description\": \"Prueba\"}")
                .post("/accounts/{id}/transferences", from.accountId())
                .then();
    }

    private static float balance(TestUser user) {
        return api(user.token()).get("/accounts/{id}", user.accountId()).then().statusCode(200)
                .extract().jsonPath().getFloat("balance");
    }

    @Test
    @Tag("smoke")
    @DisplayName("TC-170 Transferir dinero por alias")
    void transferirPorAlias() {
        TestUser source = withBalance("1000");
        TestUser target = loggedIn();
        transfer(source, target.alias(), "250.50")
                .statusCode(200)
                .body("type", equalTo("TRANSFER_OUT"))
                .body("amount", equalTo(250.50f))
                .body("counterpart.cvu", equalTo(target.cvu()))
                .body("counterpart.name", equalTo("Juan Pérez"));
        assertThat(balance(source)).isEqualTo(749.50f);
        assertThat(balance(target)).isEqualTo(250.50f);
    }

    @Test
    @DisplayName("TC-171 Transferir dinero por CVU")
    void transferirPorCvu() {
        TestUser source = withBalance("100");
        TestUser target = loggedIn();
        transfer(source, target.cvu(), "100").statusCode(200);
        assertThat(balance(source)).isZero();
        assertThat(balance(target)).isEqualTo(100f);
    }

    @Test
    @Tag("smoke")
    @DisplayName("TC-172 Fondos insuficientes")
    void fondosInsuficientes() {
        TestUser source = withBalance("100");
        TestUser target = loggedIn();
        transfer(source, target.alias(), "100.01").statusCode(410);
        assertThat(balance(source)).isEqualTo(100f);
        assertThat(balance(target)).isZero();
    }

    @Test
    @DisplayName("TC-173 Cuenta destino inexistente")
    void destinoInexistente() {
        TestUser source = withBalance("100");
        transfer(source, "no.existe.nadie", "10").statusCode(400);
        transfer(source, "0000000000000000000000", "10").statusCode(400);
    }

    @Test
    @DisplayName("TC-174 Transferir a la misma cuenta")
    void mismaCuenta() {
        TestUser source = withBalance("100");
        transfer(source, source.alias(), "10").statusCode(400);
    }

    @Test
    @DisplayName("TC-175 Transferir desde una cuenta ajena")
    void cuentaAjena() {
        TestUser owner = withBalance("100");
        TestUser thief = loggedIn();
        api(thief.token()).body("{\"destination\": \"" + thief.alias() + "\", \"amount\": 50}")
                .post("/accounts/{id}/transferences", owner.accountId())
                .then().statusCode(403);
        assertThat(balance(owner)).isEqualTo(100f);
    }

    @Test
    @DisplayName("TC-176 El destinatario ve la transferencia recibida")
    void destinatarioVeLaTransferencia() {
        TestUser source = withBalance("100");
        TestUser target = loggedIn();
        transfer(source, target.alias(), "30").statusCode(200);
        api(target.token()).get("/accounts/{id}/activity", target.accountId())
                .then().statusCode(200)
                .body("[0].type", equalTo("TRANSFER_IN"))
                .body("[0].direction", equalTo("INCOME"))
                .body("[0].counterpart.cvu", equalTo(source.cvu()));
    }

    @Test
    @DisplayName("TC-177 La clave de idempotencia evita transferir dos veces")
    void idempotencia() {
        TestUser source = withBalance("1000");
        TestUser target = loggedIn();
        String key = UUID.randomUUID().toString();
        String body = "{\"destination\": \"" + target.alias() + "\", \"amount\": 100}";
        int first = api(source.token()).header("Idempotency-Key", key).body(body)
                .post("/accounts/{id}/transferences", source.accountId())
                .then().statusCode(200).header("Idempotent-Replayed", "false")
                .extract().path("id");
        api(source.token()).header("Idempotency-Key", key).body(body)
                .post("/accounts/{id}/transferences", source.accountId())
                .then().statusCode(200).header("Idempotent-Replayed", "true")
                .body("id", equalTo(first));
        assertThat(balance(source)).isEqualTo(900f);
    }

    @Test
    @DisplayName("TC-183 Reutilizar la clave de idempotencia con otros datos")
    void claveReutilizada() {
        TestUser source = withBalance("1000");
        TestUser target = loggedIn();
        String key = UUID.randomUUID().toString();
        api(source.token()).header("Idempotency-Key", key).body("{\"destination\": \"" + target.alias() + "\", \"amount\": 100}")
                .post("/accounts/{id}/transferences", source.accountId()).then().statusCode(200);
        api(source.token()).header("Idempotency-Key", key).body("{\"destination\": \"" + target.alias() + "\", \"amount\": 1}")
                .post("/accounts/{id}/transferences", source.accountId()).then().statusCode(409);
        assertThat(balance(source)).isEqualTo(900f);
    }

    @Test
    @DisplayName("TC-184 CVU pegado con espacios o guiones")
    void cvuConEspacios() {
        TestUser source = withBalance("100");
        TestUser target = loggedIn();
        String cvu = target.cvu();
        transfer(source, cvu.substring(0, 8) + " " + cvu.substring(8, 15) + "-" + cvu.substring(15), "10").statusCode(200);
        assertThat(balance(target)).isEqualTo(10f);
    }

    @Test
    @DisplayName("TC-178 Montos inválidos en una transferencia")
    void montosInvalidos() {
        TestUser source = withBalance("100");
        TestUser target = loggedIn();
        for (String amount : new String[] { "0", "-1", "1.001", "\"10\"" }) {
            transfer(source, target.alias(), amount).statusCode(400);
        }
        assertThat(balance(source)).isEqualTo(100f);
    }

    @Test
    @Tag("smoke")
    @DisplayName("TC-180 Últimos destinatarios")
    void ultimosDestinatarios() {
        TestUser source = withBalance("100");
        TestUser first = loggedIn();
        TestUser second = loggedIn();
        transfer(source, first.alias(), "1").statusCode(200);
        transfer(source, second.alias(), "1").statusCode(200);
        transfer(source, first.alias(), "2").statusCode(200);
        api(source.token()).get("/accounts/{id}/transferences", source.accountId())
                .then().statusCode(200)
                .body("$", hasSize(2))
                .body("[0].accountId", equalTo((int) first.accountId()))
                .body("[0].lastAmount", equalTo(2.0f))
                .body("[1].accountId", equalTo((int) second.accountId()));
    }

    @Test
    @DisplayName("TC-181 Últimos destinatarios de una cuenta ajena")
    void destinatariosAjenos() {
        TestUser owner = loggedIn();
        TestUser other = loggedIn();
        api(other.token()).get("/accounts/{id}/transferences", owner.accountId()).then().statusCode(403);
    }

    @Test
    @DisplayName("TC-182 Sin transferencias no hay destinatarios")
    void sinDestinatarios() {
        TestUser user = loggedIn();
        api(user.token()).get("/accounts/{id}/transferences", user.accountId())
                .then().statusCode(200).body("$", empty());
    }

    @Test
    @DisplayName("TC-190 Descargar el comprobante en PDF")
    void comprobantePdf() {
        TestUser source = withBalance("100");
        TestUser target = loggedIn();
        int transactionId = transfer(source, target.alias(), "40").statusCode(200).extract().path("id");
        byte[] pdf = api(source.token()).accept("application/pdf")
                .get("/accounts/{id}/activity/{transactionId}/receipt", source.accountId(), transactionId)
                .then().statusCode(200)
                .contentType("application/pdf")
                .header("Content-Disposition", startsWith("attachment"))
                .extract().asByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("TC-191 Comprobante de un movimiento ajeno")
    void comprobanteAjeno() {
        TestUser source = withBalance("100");
        TestUser other = loggedIn();
        int transactionId = transfer(source, other.alias(), "40").statusCode(200).extract().path("id");
        api(other.token()).accept("application/pdf")
                .get("/accounts/{id}/activity/{transactionId}/receipt", other.accountId(), transactionId)
                .then().statusCode(404);
    }
}
