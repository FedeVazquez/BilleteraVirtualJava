package com.dmh.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dmh.accounts.dto.AccountResponse;
import com.dmh.accounts.exception.ResourceNotFoundException;
import com.dmh.accounts.model.Account;
import com.dmh.accounts.repository.AccountRepository;
import com.dmh.accounts.repository.TransactionRepository;
import com.dmh.accounts.security.AccountAccess;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String CVU = "1234567890123456789012";

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CvuGenerator cvuGenerator;
    @Mock
    private AliasGenerator aliasGenerator;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountAccess accountAccess;
    @InjectMocks
    private AccountService accountService;

    @Test
    void creaLaCuentaConCvuYAliasGenerados() {
        when(accountRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(cvuGenerator.generate()).thenReturn(CVU);
        when(aliasGenerator.generate()).thenReturn("sol.luna.mar");
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponse response = accountService.createForUser(1L);

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.cvu()).isEqualTo(CVU);
        assertThat(response.alias()).isEqualTo("sol.luna.mar");
        assertThat(response.balance()).isZero();
    }

    @Test
    void siElCvuYaExisteGeneraOtro() {
        String otherCvu = "9999999999999999999999";
        when(accountRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(cvuGenerator.generate()).thenReturn(CVU, otherCvu);
        when(accountRepository.existsByCvu(CVU)).thenReturn(true);
        when(accountRepository.existsByCvu(otherCvu)).thenReturn(false);
        when(aliasGenerator.generate()).thenReturn("sol.luna.mar");
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponse response = accountService.createForUser(1L);

        assertThat(response.cvu()).isEqualTo(otherCvu);
    }

    @Test
    void siElUsuarioYaTieneCuentaDevuelveLaExistente() {
        Account existing = new Account(1L, CVU, "sol.luna.mar");
        when(accountRepository.findByUserId(1L)).thenReturn(Optional.of(existing));

        AccountResponse response = accountService.createForUser(1L);

        assertThat(response.cvu()).isEqualTo(CVU);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void buscarCuentaInexistenteLanzaResourceNotFound() {
        when(accountRepository.findByUserId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getByUserId(99L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
