package com.dmh.users.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import com.dmh.users.event.PasswordResetRequestedEvent;
import com.dmh.users.exception.ValidationException;
import com.dmh.users.model.User;
import com.dmh.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final String EMAIL = "juan@mail.com";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ApplicationEventPublisher events;

    private PasswordResetService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, passwordEncoder, events, 30);
        user = new User("Juan", "Pérez", "30123456", EMAIL, "+5491122334455", "hash-viejo");
    }

    @Test
    void alPedirElRecuperoGuardaSoloElHashDelTokenYPublicaElEvento() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        service.request(EMAIL);

        ArgumentCaptor<PasswordResetRequestedEvent> event = ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);
        verify(events).publishEvent(event.capture());
        String token = event.getValue().token();
        assertThat(token).isNotBlank();
        assertThat(user.getResetTokenHash()).isEqualTo(PasswordResetService.hash(token)).isNotEqualTo(token);
        assertThat(user.getResetTokenExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void siElEmailNoExisteNoHaceNadaYNoInformaError() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        service.request(EMAIL);

        verify(events, never()).publishEvent(any());
    }

    @Test
    void conUnTokenValidoCambiaLaClaveYElTokenQuedaInutilizado() {
        user.startPasswordReset(PasswordResetService.hash("token-ok"), Instant.now().plusSeconds(60));
        when(userRepository.findByResetTokenHash(PasswordResetService.hash("token-ok")))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.encode("ClaveNueva1")).thenReturn("hash-nuevo");

        service.confirm("token-ok", "ClaveNueva1");

        assertThat(user.getPassword()).isEqualTo("hash-nuevo");
        assertThat(user.getResetTokenHash()).isNull();
    }

    @Test
    void rechazaUnTokenDesconocido() {
        when(userRepository.findByResetTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm("token-falso", "ClaveNueva1"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rechazaUnTokenVencido() {
        user.startPasswordReset(PasswordResetService.hash("token-ok"), Instant.now().minusSeconds(1));
        when(userRepository.findByResetTokenHash(PasswordResetService.hash("token-ok")))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.confirm("token-ok", "ClaveNueva1"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("venció");
        assertThat(user.getPassword()).isEqualTo("hash-viejo");
    }
}
