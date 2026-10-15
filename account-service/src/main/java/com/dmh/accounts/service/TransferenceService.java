package com.dmh.accounts.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dmh.accounts.client.UsersClient;
import com.dmh.accounts.dto.RecipientResponse;
import com.dmh.accounts.dto.TransactionResponse;
import com.dmh.accounts.dto.TransferenceRequest;
import com.dmh.accounts.exception.ConflictException;
import com.dmh.accounts.exception.InsufficientFundsException;
import com.dmh.accounts.exception.ValidationException;
import com.dmh.accounts.model.Account;
import com.dmh.accounts.model.Card;
import com.dmh.accounts.model.IdempotencyRecord;
import com.dmh.accounts.model.Transaction;
import com.dmh.accounts.model.TransactionType;
import com.dmh.accounts.repository.AccountRepository;
import com.dmh.accounts.repository.IdempotencyRecordRepository;
import com.dmh.accounts.repository.TransactionRepository;
import com.dmh.accounts.security.AccountAccess;


/** Operaciones que mueven dinero: ingresos desde tarjeta y transferencias entre cuentas. */
@Service
public class TransferenceService {

    private static final Logger log = LoggerFactory.getLogger(TransferenceService.class);
    private static final int RECENT_RECIPIENTS = 5;
    /** Cuantas transferencias recientes se revisan para armar la lista de destinatarios. */
    private static final int RECIPIENTS_LOOKBACK = 200;

    private final AccountAccess accountAccess;
    private final AccountRepository accountRepository;
    private final CardService cardService;
    private final TransactionRepository transactionRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final UsersClient usersClient;
    private final Clock clock;

    public TransferenceService(AccountAccess accountAccess, AccountRepository accountRepository,
            CardService cardService, TransactionRepository transactionRepository,
            IdempotencyRecordRepository idempotencyRecordRepository, UsersClient usersClient, Clock clock) {
        this.accountAccess = accountAccess;
        this.accountRepository = accountRepository;
        this.cardService = cardService;
        this.transactionRepository = transactionRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.usersClient = usersClient;
        this.clock = clock;
    }

    /** Resultado de una operacion. {@code replayed} indica que se devolvio una ya procesada con la misma clave. */
    public record OperationResult(TransactionResponse transaction, boolean replayed) {
    }

    /**
     * Ingresa dinero a la billetera desde una tarjeta propia. La cuenta se bloquea durante la
     * operacion para que dos ingresos simultaneos no se pisen el saldo.
     *
     * @throws com.dmh.accounts.exception.ResourceNotFoundException si la tarjeta no es de la cuenta (404)
     * @throws ValidationException si la tarjeta esta vencida (400)
     */
    @Transactional
    public OperationResult deposit(Long accountId, TransferenceRequest request, String idempotencyKey) {
        Account account = accountAccess.requireAccessForUpdate(accountId);
        String fingerprint = fingerprint("DEPOSIT", request.cardId(), request.amount());
        return idempotent(account.getId(), idempotencyKey, fingerprint, () -> {
            Card card = cardService.findOwnedCard(account.getId(), request.cardId());
            if (card.isExpired()) {
                throw new ValidationException("La tarjeta está vencida");
            }
            BigDecimal amount = request.amount().setScale(2);
            account.credit(amount);
            Transaction deposit = transactionRepository.save(
                    Transaction.deposit(account.getId(), amount, card, Instant.now(clock)));
            log.info("Ingreso {}: cuenta {} +{} desde tarjeta {}", deposit.getId(), account.getId(), amount, card.getId());
            return deposit;
        });
    }

    /**
     * Transfiere dinero del saldo disponible a otra cuenta de Digital Money House, identificada por
     * CVU/CBU (22 digitos) o alias. Debito y credito ocurren en la misma transaccion: o se hacen los
     * dos o ninguno.
     * <p>
     * Para que dos transferencias cruzadas (A→B y B→A al mismo tiempo) no se bloqueen mutuamente, las
     * dos cuentas se bloquean siempre en el mismo orden: primero la de id menor.
     *
     * @throws ValidationException si la cuenta destino no existe o es la misma (400)
     * @throws InsufficientFundsException si el saldo no alcanza (410)
     */
    @Transactional
    public OperationResult transfer(Long accountId, TransferenceRequest request, String idempotencyKey) {
        String destination = normalizeDestination(request.destination());
        Long destinationId = findAccountId(destination)
                .orElseThrow(() -> new ValidationException("La cuenta destino no existe: " + destination));
        if (destinationId.equals(accountId)) {
            throw new ValidationException("No se puede transferir a la misma cuenta");
        }
        Account source;
        Account target;
        if (accountId < destinationId) {
            source = accountAccess.requireAccessForUpdate(accountId);
            target = lock(destinationId);
        } else {
            target = lock(destinationId);
            source = accountAccess.requireAccessForUpdate(accountId);
        }
        String fingerprint = fingerprint("TRANSFER", destinationId, request.amount(), request.description());
        return idempotent(source.getId(), idempotencyKey, fingerprint, () -> {
            BigDecimal amount = request.amount().setScale(2);
            if (!source.hasFunds(amount)) {
                throw new InsufficientFundsException("Fondos insuficientes: el saldo disponible es $" + source.getBalance());
            }
            source.debit(amount);
            target.credit(amount);
            Instant now = Instant.now(clock);
            String description = request.description() == null || request.description().isBlank()
                    ? "Transferencia" : request.description().trim();
            Transaction outgoing = transactionRepository.save(Transaction.transfer(TransactionType.TRANSFER_OUT,
                    source, target, holderName(target), amount, description, now));
            transactionRepository.save(Transaction.transfer(TransactionType.TRANSFER_IN, target, source,
                    holderName(source), amount, description, now));
            log.info("Transferencia {}: cuenta {} -> cuenta {} por {}", outgoing.getId(), source.getId(), target.getId(), amount);
            return outgoing;
        });
    }

    /** Ultimas 5 cuentas distintas a las que se transfirio, de la mas reciente a la mas antigua. */
    @Transactional(readOnly = true)
    public List<RecipientResponse> recentRecipients(Long accountId) {
        Account account = accountAccess.requireAccess(accountId);
        Map<Long, Transaction> latestByRecipient = new LinkedHashMap<>();
        for (Transaction transfer : transactionRepository.findByAccountIdAndTypeOrderByDatedDescIdDesc(
                account.getId(), TransactionType.TRANSFER_OUT, PageRequest.of(0, RECIPIENTS_LOOKBACK))) {
            latestByRecipient.putIfAbsent(transfer.getCounterpartAccountId(), transfer);
            if (latestByRecipient.size() == RECENT_RECIPIENTS) {
                break;
            }
        }
        // CVU y alias actuales (el alias pudo cambiar desde la transferencia), nombre de la ultima operacion.
        Map<Long, Account> current = new LinkedHashMap<>();
        accountRepository.findAllById(latestByRecipient.keySet()).forEach(found -> current.put(found.getId(), found));
        List<RecipientResponse> recipients = new ArrayList<>();
        latestByRecipient.forEach((recipientId, transfer) -> {
            Account recipient = current.get(recipientId);
            recipients.add(new RecipientResponse(recipientId,
                    recipient != null ? recipient.getCvu() : transfer.getCounterpartCvu(),
                    recipient != null ? recipient.getAlias() : transfer.getCounterpartAlias(),
                    transfer.getCounterpartName(), transfer.getDated(), transfer.getAmount()));
        });
        return recipients;
    }

    /**
     * Ejecuta la operacion una sola vez por clave. La busqueda de la clave ocurre con la cuenta ya
     * bloqueada, asi que dos envios simultaneos con la misma clave se ordenan: el segundo encuentra
     * la operacion del primero y la devuelve.
     */
    private OperationResult idempotent(Long accountId, String key, String fingerprint, Supplier<Transaction> operation) {
        if (key == null || key.isBlank()) {
            return new OperationResult(TransactionResponse.from(operation.get()), false);
        }
        if (key.length() > 64) {
            throw new ValidationException("El header Idempotency-Key no puede superar los 64 caracteres");
        }
        Optional<IdempotencyRecord> previous = idempotencyRecordRepository.findByAccountIdAndIdempotencyKey(accountId, key);
        if (previous.isPresent()) {
            if (!previous.get().getRequestFingerprint().equals(fingerprint)) {
                throw new ConflictException("La clave Idempotency-Key ya se usó para una operación con otros datos");
            }
            Transaction original = transactionRepository.findById(previous.get().getTransactionId()).orElseThrow();
            return new OperationResult(TransactionResponse.from(original), true);
        }
        Transaction transaction = operation.get();
        idempotencyRecordRepository.save(
                new IdempotencyRecord(accountId, key, fingerprint, transaction.getId(), Instant.now(clock)));
        return new OperationResult(TransactionResponse.from(transaction), false);
    }

    /**
     * CVU/CBU pegado con espacios o guiones ("0000003 1000...") se limpia; un alias se pasa a minusculas.
     */
    static String normalizeDestination(String destination) {
        String trimmed = destination.trim();
        String digitsOnly = trimmed.replaceAll("[\\s-]", "");
        return digitsOnly.matches("\\d+") ? digitsOnly : trimmed.toLowerCase(Locale.ROOT);
    }

    private static String fingerprint(Object... parts) {
        StringBuilder joined = new StringBuilder();
        for (Object part : parts) {
            Object value = part instanceof BigDecimal amount ? amount.stripTrailingZeros().toPlainString() : part;
            joined.append(value).append('|');
        }
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(joined.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private Optional<Long> findAccountId(String destination) {
        if (destination.matches("\\d{22}")) {
            return accountRepository.findIdByCvu(destination);
        }
        return accountRepository.findIdByAlias(destination.toLowerCase(Locale.ROOT));
    }

    private Account lock(Long accountId) {
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ValidationException("La cuenta destino no existe"));
    }

    /**
     * Nombre del titular para el comprobante. Si users-service no responde, la transferencia se hace
     * igual y el nombre queda vacio: no es un dato necesario para mover el dinero.
     */
    private String holderName(Account account) {
        try {
            return usersClient.summary(account.getUserId()).fullName();
        } catch (RuntimeException e) {
            log.warn("No se pudo obtener el nombre del titular de la cuenta {}: {}", account.getId(), e.getMessage());
            return null;
        }
    }
}
