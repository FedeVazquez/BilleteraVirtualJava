package com.dmh.apitests.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.restassured.RestAssured;

/**
 * Lee los mails que "envio" el sistema, para obtener el codigo de verificacion o el link de recupero.
 * <p>
 * Fuente configurable con la propiedad {@code mail.source}:
 * <ul>
 * <li>{@code mailhog:http://localhost:8025} — la API de MailHog (entorno con Docker).</li>
 * <li>{@code file:C:/ruta/mails.jsonl} — la salida de {@code scripts/fake_smtp.py} (entorno sin Docker).</li>
 * </ul>
 */
public final class Mailbox {

    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");
    private static final Pattern RESET_TOKEN = Pattern.compile("token=([\\w-]+)");
    private static final ObjectMapper JSON = new ObjectMapper();

    private Mailbox() {
    }

    /** Ultimo codigo de verificacion enviado a ese email. Espera hasta 10 s a que llegue el mail. */
    public static String verificationCode(String email) {
        return lastMatch(email, "código de verificación", CODE);
    }

    /** Token del ultimo link de recupero de contraseña enviado a ese email. */
    public static String resetToken(String email) {
        return lastMatch(email, "token=", RESET_TOKEN);
    }

    /** Cuerpos de los mails recibidos por esa direccion, del mas viejo al mas nuevo. */
    public static List<String> bodies(String email) {
        String source = TestConfig.mailSource();
        try {
            if (source.startsWith("file:")) {
                return fromFile(Path.of(source.substring("file:".length())), email);
            }
            if (source.startsWith("mailhog:")) {
                return fromMailhog(source.substring("mailhog:".length()), email);
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudieron leer los mails de " + source, e);
        }
        throw new IllegalArgumentException("mail.source debe empezar con mailhog: o file: (" + source + ")");
    }

    private static String lastMatch(String email, String marker, Pattern pattern) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (true) {
            List<String> bodies = bodies(email);
            for (int i = bodies.size() - 1; i >= 0; i--) {
                if (bodies.get(i).contains(marker)) {
                    Matcher matcher = pattern.matcher(bodies.get(i));
                    if (matcher.find()) {
                        return matcher.group(1);
                    }
                }
            }
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("No llegó un mail con \"" + marker + "\" a " + email);
            }
            sleep();
        }
    }

    private static List<String> fromFile(Path file, String email) throws IOException {
        List<String> bodies = new ArrayList<>();
        if (!Files.exists(file)) {
            return bodies;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode mail = JSON.readTree(line);
            for (JsonNode to : mail.get("to")) {
                if (to.asText().equalsIgnoreCase(email)) {
                    bodies.add(mail.get("body").asText());
                }
            }
        }
        return bodies;
    }

    private static List<String> fromMailhog(String baseUrl, String email) throws IOException {
        String response = RestAssured.given().baseUri(baseUrl)
                .queryParam("kind", "to")
                .queryParam("query", email)
                .get("/api/v2/search")
                .asString();
        JsonNode items = JSON.readTree(response).path("items");
        List<String> bodies = new ArrayList<>();
        for (JsonNode item : items) {
            // MailHog devuelve primero el mas nuevo; se invierte para mantener el orden cronologico.
            bodies.add(0, decodeQuotedPrintable(item.path("Content").path("Body").asText()));
        }
        return bodies;
    }

    /** Los mails con acentos viajan en "quoted-printable": =C3=A9 por é y "=" al final de linea cortada. */
    static String decodeQuotedPrintable(String text) {
        String joined = text.replace("=\r\n", "").replace("=\n", "");
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < joined.length(); i++) {
            char c = joined.charAt(i);
            if (c == '=' && i + 2 < joined.length() && isHex(joined.charAt(i + 1)) && isHex(joined.charAt(i + 2))) {
                bytes.write(Integer.parseInt(joined.substring(i + 1, i + 3), 16));
                i += 2;
            } else {
                byte[] encoded = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                bytes.write(encoded, 0, encoded.length);
            }
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static boolean isHex(char c) {
        return Character.digit(c, 16) >= 0;
    }

    private static void sleep() {
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
