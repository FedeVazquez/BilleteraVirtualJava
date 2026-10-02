package com.dmh.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.dmh.auth.client.UsersClient;
import com.dmh.auth.dto.CredentialsResponse;
import com.dmh.auth.exception.ResourceNotFoundException;
import com.dmh.auth.exception.ValidationException;
import com.dmh.auth.repository.RevokedTokenRepository;
import com.dmh.auth.repository.UserSessionRevocationRepository;
import com.dmh.auth.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;

import feign.FeignException;

/**
 * Test de integracion: levanta el contexto completo contra H2 en memoria.
 * users-service se reemplaza por un mock del cliente Feign.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiTest {

    private static final String EMAIL = "juan@mail.com";
    private static final String PASSWORD = "Clave1234";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private RevokedTokenRepository revokedTokenRepository;
    @Autowired
    private UserSessionRevocationRepository userSessionRevocationRepository;

    @MockitoBean
    private UsersClient usersClient;

    @BeforeEach
    void setUp() {
        revokedTokenRepository.deleteAll();
        userSessionRevocationRepository.deleteAll();
    }

    private void existeElUsuario(boolean emailVerified) {
        when(usersClient.getCredentials(EMAIL)).thenReturn(new CredentialsResponse(7L, EMAIL,
                passwordEncoder.encode(PASSWORD), List.of("ROLE_USER"), emailVerified));
    }

    private static String loginJson(String email, String password) {
        return "{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}";
    }

    private String login() throws Exception {
        existeElUsuario(true);
        MvcResult result = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    // ---------- login ----------

    @Test
    void loginCorrectoDevuelve200ConUnTokenValido() throws Exception {
        String token = login();

        assertThat(jwtService.parse(token).getSubject()).isEqualTo("7");
    }

    @Test
    void laRespuestaDelLoginIndicaTipoYVencimientoDelToken() throws Exception {
        existeElUsuario(true);

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void loginDeUnUsuarioInexistenteDevuelve404() throws Exception {
        when(usersClient.getCredentials("nadie@mail.com"))
                .thenThrow(new ResourceNotFoundException("No existe un usuario con ese email"));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("nadie@mail.com", PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void loginConContraseniaIncorrectaDevuelve400() throws Exception {
        existeElUsuario(true);

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(EMAIL, "OtraClave99")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Contraseña incorrecta"));
    }

    @Test
    void loginSinVerificarElEmailDevuelve403() throws Exception {
        existeElUsuario(false);

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(EMAIL, PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginSinDatosDevuelve400() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.length()").value(2));
    }

    @Test
    void siUsersServiceNoRespondeElLoginDevuelve500() throws Exception {
        when(usersClient.getCredentials(EMAIL)).thenThrow(FeignException.class);

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(EMAIL, PASSWORD)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));
    }

    // ---------- validate / logout ----------

    @Test
    void unTokenRecienEmitidoEsValido() throws Exception {
        String token = login();

        mockMvc.perform(get("/auth/validate").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));
    }

    @Test
    void despuesDelLogoutElTokenDejaDeSerValido() throws Exception {
        String token = login();

        mockMvc.perform(post("/user/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/auth/validate").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elLogoutInvalidaSoloEseTokenYEsIdempotente() throws Exception {
        String first = login();
        String second = login();

        mockMvc.perform(post("/user/logout").header("Authorization", "Bearer " + first)).andExpect(status().isOk());
        mockMvc.perform(post("/user/logout").header("Authorization", "Bearer " + first)).andExpect(status().isOk());

        mockMvc.perform(get("/auth/validate").header("Authorization", "Bearer " + second))
                .andExpect(status().isOk());
        assertThat(revokedTokenRepository.count()).isEqualTo(1);
    }

    @Test
    void logoutSinTokenOConTokenInvalidoDevuelve401() throws Exception {
        mockMvc.perform(post("/user/logout")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/user/logout").header("Authorization", "Bearer no.es.un-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void validarSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/auth/validate")).andExpect(status().isUnauthorized());
    }

    // ---------- verificacion de email y recupero de contraseña ----------

    @Test
    void verificarEmailConCodigoCorrectoDevuelve200() throws Exception {
        mockMvc.perform(post("/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + EMAIL + "\", \"code\": \"123456\"}"))
                .andExpect(status().isOk());

        verify(usersClient).confirmEmail(any());
    }

    @Test
    void verificarEmailConCodigoIncorrectoDevuelve400ConElMensajeDeUsersService() throws Exception {
        doThrow(new ValidationException("El código de verificación es incorrecto"))
                .when(usersClient).confirmEmail(any());

        mockMvc.perform(post("/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + EMAIL + "\", \"code\": \"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El código de verificación es incorrecto"));
    }

    @Test
    void verificarEmailConUnCodigoQueNoTieneSeisDigitosDevuelve400SinLlamarAUsersService() throws Exception {
        mockMvc.perform(post("/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + EMAIL + "\", \"code\": \"12ab\"}"))
                .andExpect(status().isBadRequest());

        verify(usersClient, never()).confirmEmail(any());
    }

    @Test
    void pedirElRecuperoDeContraseniaDevuelve200() throws Exception {
        mockMvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + EMAIL + "\"}"))
                .andExpect(status().isOk());

        verify(usersClient).requestPasswordReset(any());
    }

    @Test
    void cambiarLaContraseniaConLasDosClavesIgualesDevuelve200() throws Exception {
        when(usersClient.confirmPasswordReset(any())).thenReturn(new UsersClient.PasswordResetResult(7L));

        mockMvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"abc\", \"password\": \"ClaveNueva1\", \"passwordConfirm\": \"ClaveNueva1\"}"))
                .andExpect(status().isOk());

        verify(usersClient).confirmPasswordReset(new UsersClient.NewPasswordRequest("abc", "ClaveNueva1"));
    }

    @Test
    void cambiarLaContraseniaCierraLasSesionesAbiertasPeroPermiteVolverAIngresar() throws Exception {
        String tokenAnterior = login();
        when(usersClient.confirmPasswordReset(any())).thenReturn(new UsersClient.PasswordResetResult(7L));

        mockMvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"abc\", \"password\": \"ClaveNueva1\", \"passwordConfirm\": \"ClaveNueva1\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/auth/validate").header("Authorization", "Bearer " + tokenAnterior))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/auth/validate").header("Authorization", "Bearer " + login()))
                .andExpect(status().isOk());
    }

    @Test
    void cambiarLaContraseniaConClavesDistintasDevuelve400SinLlamarAUsersService() throws Exception {
        mockMvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"abc\", \"password\": \"ClaveNueva1\", \"passwordConfirm\": \"ClaveNueva2\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Las contraseñas no coinciden"));

        verify(usersClient, never()).confirmPasswordReset(any());
    }

    @Test
    void laPantallaParaElegirLaNuevaContraseniaEstaDisponible() throws Exception {
        mockMvc.perform(get("/auth/reset-password.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }
}
