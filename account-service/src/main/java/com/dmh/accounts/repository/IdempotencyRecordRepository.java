package com.dmh.accounts.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dmh.accounts.model.IdempotencyRecord;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByAccountIdAndIdempotencyKey(Long accountId, String idempotencyKey);
}
