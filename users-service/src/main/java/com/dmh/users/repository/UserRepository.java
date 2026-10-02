package com.dmh.users.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dmh.users.model.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByResetTokenHash(String resetTokenHash);

    boolean existsByEmail(String email);

    boolean existsByDni(String dni);
}
