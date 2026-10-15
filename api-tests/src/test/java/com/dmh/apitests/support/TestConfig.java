package com.dmh.apitests.support;

/** Configuracion de la ejecucion, leida de propiedades del sistema (ver pom.xml del modulo). */
public final class TestConfig {

    private TestConfig() {
    }

    public static String baseUrl() {
        return System.getProperty("api.baseUrl", "http://localhost:8090");
    }

    public static String eurekaUrl() {
        return System.getProperty("api.eurekaUrl", "http://localhost:8761");
    }

    public static String mailSource() {
        return System.getProperty("mail.source", "mailhog:http://localhost:8025");
    }
}
