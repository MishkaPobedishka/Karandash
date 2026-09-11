package ru.karandash.core.account;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Привязка пользователя Telegram к аккаунту: при первом сообщении аккаунт заводится автоматически.
 */
@Service
public class TelegramAccountService {

    private final AccountRepository accounts;
    private final TelegramIdentityRepository identities;
    private final TransactionTemplate transactions;

    public TelegramAccountService(
            AccountRepository accounts,
            TelegramIdentityRepository identities,
            TransactionTemplate transactions
    ) {
        this.accounts = accounts;
        this.identities = identities;
        this.transactions = transactions;
    }

    public UUID resolveAccount(long telegramId) {
        try {
            return transactions.execute(status -> findOrRegister(telegramId));
        } catch (ConcurrentRegistrationException exception) {
            // Первое и второе сообщения нового пользователя пришли одновременно — привязку уже создал соседний запрос.
            return transactions.execute(status -> findOrRegister(telegramId));
        }
    }

    private UUID findOrRegister(long telegramId) {
        return identities.findById(telegramId)
                .map(TelegramIdentityEntity::getAccountId)
                .orElseGet(() -> register(telegramId));
    }

    private UUID register(long telegramId) {
        UUID accountId = UUID.randomUUID();
        accounts.saveAndFlush(new AccountEntity(accountId, AccountStatus.ACTIVE));
        if (identities.insertIfAbsent(telegramId, accountId) == 0) {
            throw new ConcurrentRegistrationException();
        }
        return accountId;
    }

    static final class ConcurrentRegistrationException extends RuntimeException {

        ConcurrentRegistrationException() {
            super("Пользователь Telegram уже привязан параллельным запросом");
        }
    }
}
