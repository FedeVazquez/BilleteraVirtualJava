package com.dmh.users.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.dmh.users.event.VerificationCodeIssuedEvent;
import com.dmh.users.exception.ResourceNotFoundException;
import com.dmh.users.exception.ValidationException;
import com.dmh.users.model.User;
import com.dmh.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final String EMAIL = "juan@mail.com";
    private static final int MAX_ATTEMPTS = 3;

    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationEventPublisher events;

    private EmailVerificationService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(userRepository, events, 15, MAX_ATTEMPTS);
        user = new User("Juan", "Pérez", "30123456", EMAIL, "+5491122334455", "hash");
    }

    @Test
    void generaUnCodigoDeSeisDigitosYPublicaElEvento() {
        service.issueCode(user);

        ArgumentCaptor<VerificationCodeIssuedEvent> event = ArgumentCaptor.forClass(VerificationCodeIssuedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().code()).matches("\\d{6}").isEqualTo(user.getVerificationCode());
        assertThat(event.getValue().email()).isEqualTo(EMAIL);
        assertThat(user.getVerificationCodeExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void conElCodigoCorrectoMarcaElEmailComoVerificado() {
        user.startEmailVerification("123456", Instant.now().plusSeconds(60));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        service.confirm(EMAIL, "123456");

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getVerificationCode()).isNull();
    }

    @Test
    void conUnCodigoIncorrectoRechazaYCuentaElIntento() {
        user.startEmailVerification("123456", Instant.now().plusSeconds(60));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.confirm(EMAIL, "000000")).isInstanceOf(ValidationException.class);

        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getVerificationAttempts()).isEqualTo(1);
    }

    @Test
    void alSuperarLosIntentosRechazaInclusoElCodigoCorrecto() {
        user.startEmailVerification("123456", Instant.now().plusSeconds(60));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            assertThatThrownBy(() -> service.confirm(EMAIL, "000000")).isInstanceOf(ValidationException.class);
        }

        assertThatThrownBy(() -> service.confirm(EMAIL, "123456"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("intentos");
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void rechazaUnCodigoVencido() {
        user.startEmailVerification("123456", Instant.now().minusSeconds(1));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.confirm(EMAIL, "123456"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("venció");
    }

    @Test
    void verificarUnEmailInexistenteLanzaResourceNotFound() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(EMAIL, "123456")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void noReenviaElCodigoSiElEmailYaEstaVerificado() {
        user.markEmailVerified();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.resend(EMAIL)).isInstanceOf(ValidationException.class);
    }
}
