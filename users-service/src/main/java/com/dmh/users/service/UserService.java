package com.dmh.users.service;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dmh.users.client.AccountClient;
import com.dmh.users.dto.AccountResponse;
import com.dmh.users.dto.CreateAccountRequest;
import com.dmh.users.dto.CredentialsResponse;
import com.dmh.users.dto.RegisterRequest;
import com.dmh.users.dto.UpdateUserRequest;
import com.dmh.users.dto.UserResponse;
import com.dmh.users.exception.ResourceNotFoundException;
import com.dmh.users.exception.ValidationException;
import com.dmh.users.model.Role;
import com.dmh.users.model.User;
import com.dmh.users.repository.RoleRepository;
import com.dmh.users.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountClient accountClient;
    private final EmailVerificationService emailVerificationService;

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
            PasswordEncoder passwordEncoder, AccountClient accountClient,
            EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountClient = accountClient;
        this.emailVerificationService = emailVerificationService;
    }

    /**
     * Registra al usuario y le crea su cuenta digital (CVU y alias) en account-service.
     * <p>
     * Todo ocurre en una transaccion: si account-service falla, la excepcion hace rollback del
     * usuario recien insertado y no queda un usuario sin cuenta.
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ValidationException("Ya existe un usuario registrado con ese email");
        }
        if (userRepository.existsByDni(request.dni())) {
            throw new ValidationException("Ya existe un usuario registrado con ese DNI");
        }
        Role userRole = roleRepository.findByName(Role.USER)
                .orElseThrow(() -> new IllegalStateException("Falta el rol " + Role.USER + " en la base"));

        User user = new User(request.firstName().trim(), request.lastName().trim(), request.dni(), email,
                request.phone(), passwordEncoder.encode(request.password()));
        user.addRole(userRole);
        emailVerificationService.issueCode(user);
        user = userRepository.save(user);

        AccountResponse account = accountClient.create(new CreateAccountRequest(user.getId()));
        return UserResponse.from(user, account);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el usuario " + id));
    }

    /**
     * Edita los datos personales. Email y DNI no se modifican: identifican al usuario y el email ya
     * fue verificado.
     */
    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        if (request.firstName() == null && request.lastName() == null && request.phone() == null) {
            throw new ValidationException("Indique al menos un dato para modificar: firstName, lastName o phone");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el usuario " + id));
        user.updateProfile(trim(request.firstName()), trim(request.lastName()), request.phone());
        return UserResponse.from(user);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    @Transactional(readOnly = true)
    public CredentialsResponse getCredentials(String email) {
        return userRepository.findByEmail(normalizeEmail(email))
                .map(CredentialsResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("No existe un usuario con ese email"));
    }

    static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
