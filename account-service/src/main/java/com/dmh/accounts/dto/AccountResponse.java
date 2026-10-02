package com.dmh.accounts.dto;

import java.math.BigDecimal;

import com.dmh.accounts.model.Account;

public record AccountResponse(Long id, Long userId, String cvu, String alias, BigDecimal balance) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getUserId(), account.getCvu(), account.getAlias(),
                account.getBalance());
    }
}
