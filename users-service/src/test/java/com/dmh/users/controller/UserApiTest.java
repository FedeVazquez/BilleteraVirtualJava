package com.dmh.users.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.dmh.users.client.AccountClient;
import com.dmh.users.dto.AccountResponse;
import com.dmh.users.model.User;
import com.dmh.users.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import feign.FeignException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Test de integracion: levanta el contexto completo (seguridad incluida) contra H2 en memoria.
 * account-service y el servidor de correo se reemplazan por mocks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserApiTest {

    private static final String CVU = "1234567890123456789012";
    private static final String INTERNAL_HEADER = "X-Internal-Api-Key";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private AccountClient accountClient;
    @MockitoBean
    private JavaMailSender mailSender;

    @Value("${app.jwt.secret}")
    private String jwtSecret;
    @Value("${app.internal-api-key}")
    private String internalApiKey;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        when(accountClient.create(any()))
                .thenReturn(new AccountResponse(1L, 1L, CVU, "sol.luna.mar", BigDecimal.ZERO));
    }

    @Test
    void registroCorrectoDevuelve201ConCvuYAliasYSinLaClave() throws Exception {
        mockMvc.perform(post("/users/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("juan@mail.com", "30123456")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.firstName").value("Juan"))
                .andExpect(jsonPath("$.email").value("juan@mail.com"))
                .andExpect(jsonPath("$.cvu").value(CVU))
                .andExpect(jsonPath("$.alias").value("sol.luna.mar"))
                .andExpect(jsonPath("$.password").doesNotExist());

        User saved = userRepository.findByEmail("juan@mail.com").orElseThrow();
        assertThat(saved.getPassword()).isNotEqualTo("Clave1234").startsWith("$2");
        assertThat(saved.isEmailVerified()).isFalse();
    }

    @Test
    void elRegistroEnviaPorMailElCodigoDeVerificacion() throws Exception {
        registrar("juan@mail.com", "30123456");

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        String code = userRepository.findByEmail("juan@mail.com").orElseThrow().getVerificationCode();
        assertThat(mail.getValue().getTo()).containsExactly("juan@mail.com");
        assertThat(mail.getValue().getText()).contains(code);
    }

    @Test
    void registroConDatosInvalidosDevuelve400ConElDetallePorCampo() throws Exception {
        String body = """
                {"firstName": "", "lastName": "Pérez", "dni": "abc", "email": "no-es-un-email",
                 "phone": "+5491122334455", "password": "corta"}
                """;

        mockMvc.perform(post("/users/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details", hasItem(startsWith("firstName:"))))
                .andExpect(jsonPath("$.details", hasItem(startsWith("dni:"))))
                .andExpect(jsonPath("$.details", hasItem(startsWith("email:"))))
                .andExpect(jsonPath("$.details", hasItem(startsWith("password:"))));
    }

    @Test
    void registroRechazaEmailSinDominioCompletoNombreConSimbolosYDniNumerico() throws Exception {
        String body = """
                {"firstName": "<script>alert(1)</script>", "lastName": "Pérez", "dni": "30123456",
                 "email": "a@b", "phone": "+5491122334455", "password": "Clave1234"}
                """;
        mockMvc.perform(post("/users/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(startsWith("firstName:"))))
                .andExpect(jsonPath("$.details", hasItem(startsWith("email:"))));

        // el DNI debe viajar como texto: un numero JSON se rechaza
        String dniNumerico = """
                {"firstName": "Juan", "lastName": "Pérez", "dni": 30123456,
                 "email": "juan@mail.com", "phone": "+5491122334455", "password": "Clave1234"}
                """;
        mockMvc.perform(post("/users/register").contentType(MediaType.APPLICATION_JSON).content(dniNumerico))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registroAceptaNombresConAcentosEniesApostrofesYGuiones() throws Exception {
        String body = """
                {"firstName": "María José", "lastName": "Núñez O'Higgins-Peña", "dni": "30123456",
                 "email": "maria@mail.com", "phone": "+5491122334455", "password": "Clave1234"}
                """;
        mockMvc.perform(post("/users/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lastName").value("Núñez O'Higgins-Peña"));
    }

    @Test
    void registroConJsonMalFormadoDevuelve400() throws Exception {
        mockMvc.perform(post("/users/register").contentType(MediaType.APPLICATION_JSON).content("{esto no es json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registroConEmailRepetidoDevuelve400() throws Exception {
        registrar("juan@mail.com", "30123456");

        mockMvc.perform(post("/users/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("JUAN@mail.com", "30999999")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ya existe un usuario registrado con ese email"));
    }

    @Test
    void siFallaLaCreacionDeLaCuentaDevuelve500YNoQuedaElUsuarioGuardado() throws Exception {
        when(accountClient.create(any())).thenThrow(FeignException.class);

        mockMvc.perform(post("/users/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("juan@mail.com", "30123456")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));

        assertThat(userRepository.findByEmail("juan@mail.com")).isEmpty();
    }

    @Test
    void elUsuarioPuedeConsultarSusPropiosDatos() throws Exception {
        long id = registrar("juan@mail.com", "30123456");

        mockMvc.perform(get("/users/" + id).header("Authorization", "Bearer " + token(id, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("juan@mail.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void unUsuarioNoPuedeConsultarLosDatosDeOtro() throws Exception {
        long id = registrar("juan@mail.com", "30123456");

        mockMvc.perform(get("/users/" + id).header("Authorization", "Bearer " + token(id + 1, "ROLE_USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unAdministradorPuedeConsultarCualquierUsuario() throws Exception {
        long id = registrar("juan@mail.com", "30123456");

        mockMvc.perform(get("/users/" + id).header("Authorization", "Bearer " + token(id + 1, "ROLE_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void consultarUnUsuarioSinTokenOConTokenInvalidoDevuelve401() throws Exception {
        mockMvc.perform(get("/users/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/users/1").header("Authorization", "Bearer token.invalido.x"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void losEndpointsInternosExigenLaClaveInterna() throws Exception {
        registrar("juan@mail.com", "30123456");

        mockMvc.perform(get("/internal/users/credentials").param("email", "juan@mail.com"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/users/credentials").param("email", "juan@mail.com")
                .header(INTERNAL_HEADER, "clave-incorrecta"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/users/credentials").param("email", "juan@mail.com")
                .header(INTERNAL_HEADER, internalApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"))
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    void verificarElEmailConElCodigoRecibido() throws Exception {
        registrar("juan@mail.com", "30123456");
        String code = userRepository.findByEmail("juan@mail.com").orElseThrow().getVerificationCode();
        String wrongCode = code.equals("000000") ? "111111" : "000000";

        mockMvc.perform(post("/internal/users/email-verification/confirm").header(INTERNAL_HEADER, internalApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"juan@mail.com\", \"code\": \"" + wrongCode + "\"}"))
                .andExpect(status().isBadRequest());
        // el intento fallido queda registrado aunque la respuesta haya sido un error
        assertThat(userRepository.findByEmail("juan@mail.com").orElseThrow().getVerificationAttempts()).isEqualTo(1);

        mockMvc.perform(post("/internal/users/email-verification/confirm").header(INTERNAL_HEADER, internalApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"juan@mail.com\", \"code\": \"" + code + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(userRepository.findByEmail("juan@mail.com").orElseThrow().isEmailVerified()).isTrue();
    }

    @Test
    void elUsuarioPuedeEditarNombreApellidoYTelefono() throws Exception {
        long id = registrar("juan@mail.com", "30123456");

        mockMvc.perform(patch("/users/" + id).header("Authorization", "Bearer " + token(id, "ROLE_USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\": \"Juan Manuel\", \"phone\": \"+5491155556666\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Juan Manuel"))
                .andExpect(jsonPath("$.lastName").value("Pérez"))
                .andExpect(jsonPath("$.phone").value("+5491155556666"))
                .andExpect(jsonPath("$.email").value("juan@mail.com"));
    }

    @Test
    void editarSinDatosOConDatosInvalidosDevuelve400() throws Exception {
        long id = registrar("juan@mail.com", "30123456");
        String bearer = "Bearer " + token(id, "ROLE_USER");

        mockMvc.perform(patch("/users/" + id).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/users/" + id).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\": \"   \", \"phone\": \"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.length()").value(2));
    }

    @Test
    void elEmailYElDniNoSeModificanAunqueVenganEnElJson() throws Exception {
        long id = registrar("juan@mail.com", "30123456");

        mockMvc.perform(patch("/users/" + id).header("Authorization", "Bearer " + token(id, "ROLE_USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lastName\": \"Gómez\", \"email\": \"otro@mail.com\", \"dni\": \"99999999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("juan@mail.com"))
                .andExpect(jsonPath("$.dni").value("30123456"));
    }

    @Test
    void unUsuarioNoPuedeEditarLosDatosDeOtro() throws Exception {
        long id = registrar("juan@mail.com", "30123456");

        mockMvc.perform(patch("/users/" + id).header("Authorization", "Bearer " + token(id + 1, "ROLE_USER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\": \"Intruso\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void elRegistroDevuelveElIdDeLaCuenta() throws Exception {
        mockMvc.perform(post("/users/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("juan@mail.com", "30123456")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(1));
    }

    private long registrar(String email, String dni) throws Exception {
        MvcResult result = mockMvc.perform(post("/users/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(email, dni)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private static String registerJson(String email, String dni) {
        return """
                {"firstName": "Juan", "lastName": "Pérez", "dni": "%s", "email": "%s",
                 "phone": "+5491122334455", "password": "Clave1234"}
                """.formatted(dni, email);
    }

    private String token(long userId, String role) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", "user" + userId + "@test.com")
                .claim("roles", List.of(role))
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
