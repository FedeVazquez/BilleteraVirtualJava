package com.dmh.users.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.dmh.users.client.AccountClient;
import com.dmh.users.dto.AccountResponse;
import com.dmh.users.dto.RegisterRequest;
import com.dmh.users.dto.UserResponse;
import com.dmh.users.exception.ResourceNotFoundException;
import com.dmh.users.exception.ValidationException;
import com.dmh.users.model.Role;
import com.dmh.users.model.User;
import com.dmh.users.repository.RoleRepository;
import com.dmh.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String CVU = "1234567890123456789012";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AccountClient accountClient;
    @Mock
    private EmailVerificationService emailVerificationService;
    @InjectMocks
    private UserService userService;

    private static RegisterRequest request() {
        return new RegisterRequest("Juan", "Pérez", "30123456", "  Juan.Perez@Mail.com ", "+5491122334455",
                "Clave1234");
    }

    @Test
    void registraAlUsuarioConLaClaveHasheadaElRolUserYSuCuenta() {
        when(roleRepository.findByName(Role.USER)).thenReturn(Optional.of(new Role(Role.USER)));
        when(passwordEncoder.encode("Clave1234")).thenReturn("hash-bcrypt");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountClient.create(any()))
                .thenReturn(new AccountResponse(1L, 1L, CVU, "sol.luna.mar", BigDecimal.ZERO));

        UserResponse response = userService.register(request());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPassword()).isEqualTo("hash-bcrypt");
        assertThat(saved.getValue().getRoles()).extracting(Role::getName).containsExactly(Role.USER);
        // el email se guarda normalizado (sin espacios y en minusculas)
        assertThat(saved.getValue().getEmail()).isEqualTo("juan.perez@mail.com");
        verify(emailVerificationService).issueCode(saved.getValue());

        assertThat(response.email()).isEqualTo("juan.perez@mail.com");
        assertThat(response.cvu()).isEqualTo(CVU);
        assertThat(response.alias()).isEqualTo("sol.luna.mar");
    }

    @Test
    void rechazaUnEmailYaRegistrado() {
        when(userRepository.existsByEmail("juan.perez@mail.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("email");
        verify(userRepository, never()).save(any());
        verify(accountClient, never()).create(any());
    }

    @Test
    void rechazaUnDniYaRegistrado() {
        when(userRepository.existsByDni("30123456")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("DNI");
        verify(userRepository, never()).save(any());
    }

    @Test
    void buscarCredencialesDeUnEmailInexistenteLanzaResourceNotFound() {
        when(userRepository.findByEmail("nadie@mail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCredentials("nadie@mail.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
