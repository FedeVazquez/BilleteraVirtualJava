package com.dmh.accounts.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autentica las llamadas entre microservicios a /internal/**: si el header X-Internal-Api-Key coincide
 * con la clave compartida, la peticion queda autenticada con el rol INTERNAL.
 */
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Api-Key";
    public static final String ROLE_INTERNAL = "ROLE_INTERNAL";

    private final byte[] expectedKey;

    public InternalApiKeyFilter(String expectedKey) {
        this.expectedKey = expectedKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        // Comparacion en tiempo constante para no filtrar informacion de la clave.
        if (key != null && MessageDigest.isEqual(expectedKey, key.getBytes(StandardCharsets.UTF_8))) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "internal-service", null, List.of(new SimpleGrantedAuthority(ROLE_INTERNAL))));
        }
        chain.doFilter(request, response);
    }
}
