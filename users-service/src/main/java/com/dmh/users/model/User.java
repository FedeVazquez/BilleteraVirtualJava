package com.dmh.users.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 60)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 60)
    private String lastName;

    @Column(nullable = false, unique = true, length = 20)
    private String dni;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(nullable = false, length = 20)
    private String phone;

    /** Hash BCrypt de la contraseña. Nunca se guarda ni se devuelve en texto plano. */
    @Column(nullable = false, length = 100)
    private String password;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "verification_code", length = 6)
    private String verificationCode;

    @Column(name = "verification_code_expires_at")
    private Instant verificationCodeExpiresAt;

    @Column(name = "verification_attempts", nullable = false)
    private int verificationAttempts;

    /** Hash SHA-256 del token de recupero de contraseña (el token original solo viaja por mail). */
    @Column(name = "reset_token_hash", unique = true, length = 64)
    private String resetTokenHash;

    @Column(name = "reset_token_expires_at")
    private Instant resetTokenExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
        // requerido por JPA
    }

    public User(String firstName, String lastName, String dni, String email, String phone, String password) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.dni = dni;
        this.email = email;
        this.phone = phone;
        this.password = password;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    /** Actualiza los datos personales editables; los null se dejan como estaban. */
    public void updateProfile(String newFirstName, String newLastName, String newPhone) {
        if (newFirstName != null) {
            this.firstName = newFirstName;
        }
        if (newLastName != null) {
            this.lastName = newLastName;
        }
        if (newPhone != null) {
            this.phone = newPhone;
        }
    }

    public void addRole(Role role) {
        roles.add(role);
    }

    public void startEmailVerification(String code, Instant expiresAt) {
        this.verificationCode = code;
        this.verificationCodeExpiresAt = expiresAt;
        this.verificationAttempts = 0;
    }

    public void registerFailedVerificationAttempt() {
        this.verificationAttempts++;
    }

    public void markEmailVerified() {
        this.emailVerified = true;
        this.verificationCode = null;
        this.verificationCodeExpiresAt = null;
        this.verificationAttempts = 0;
    }

    public void startPasswordReset(String tokenHash, Instant expiresAt) {
        this.resetTokenHash = tokenHash;
        this.resetTokenExpiresAt = expiresAt;
    }

    public void changePassword(String newPasswordHash) {
        this.password = newPasswordHash;
        this.resetTokenHash = null;
        this.resetTokenExpiresAt = null;
    }

    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getDni() {
        return dni;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getPassword() {
        return password;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public Instant getVerificationCodeExpiresAt() {
        return verificationCodeExpiresAt;
    }

    public int getVerificationAttempts() {
        return verificationAttempts;
    }

    public String getResetTokenHash() {
        return resetTokenHash;
    }

    public Instant getResetTokenExpiresAt() {
        return resetTokenExpiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
