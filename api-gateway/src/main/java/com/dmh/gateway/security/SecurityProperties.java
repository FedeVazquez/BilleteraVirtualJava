package com.dmh.gateway.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

/**
 * Rutas publicas del gateway, leidas de "app.security.public-paths".
 * Cada entrada tiene la forma "METODO /patron" (por ejemplo "POST /users/register" o "ANY /auth/**").
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(List<String> publicPaths) {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    public SecurityProperties {
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
    }

    public boolean isPublic(HttpMethod method, String path) {
        for (String entry : publicPaths) {
            String[] parts = entry.trim().split("\\s+", 2);
            if (parts.length != 2) {
                throw new IllegalStateException("Ruta publica mal configurada: \"" + entry + "\"");
            }
            boolean methodMatches = "ANY".equalsIgnoreCase(parts[0]) || method.name().equalsIgnoreCase(parts[0]);
            if (methodMatches && PATH_MATCHER.match(parts[1], path)) {
                return true;
            }
        }
        return false;
    }
}
