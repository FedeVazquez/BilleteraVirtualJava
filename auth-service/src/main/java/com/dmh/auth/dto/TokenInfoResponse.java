package com.dmh.auth.dto;

import java.util.List;

/** Datos del usuario dueño de un token valido. */
public record TokenInfoResponse(Long userId, String email, List<String> roles) {
}
