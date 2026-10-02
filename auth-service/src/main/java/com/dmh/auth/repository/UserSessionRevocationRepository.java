package com.dmh.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dmh.auth.model.UserSessionRevocation;

public interface UserSessionRevocationRepository extends JpaRepository<UserSessionRevocation, Long> {
}
