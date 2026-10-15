package com.dmh.accounts.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dmh.accounts.model.Card;

public interface CardRepository extends JpaRepository<Card, Long> {

    List<Card> findByAccountIdOrderByCreatedAtDescIdDesc(Long accountId);

    Optional<Card> findByIdAndAccountId(Long id, Long accountId);

    Optional<Card> findByNumberFingerprint(String numberFingerprint);

    long countByAccountId(Long accountId);
}
