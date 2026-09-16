package ru.karandash.core.account;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Привязка пользователя Telegram к аккаунту: при первом сообщении аккаунт заводится автоматически,
 * но доступ к боту у него ещё не открыт — сначала заявка, потом решение администратора.
 */
@Service
@EnableConfigurationProperties(AccessProperties.class)
public class TelegramAccountService {

    private final AccountRepository accounts;
    private final TelegramIdentityRepository identities;
    private final AccessProperties properties;
    private final TransactionTemplate transactions;

    public TelegramAccountService(
            AccountRepository accounts,
            TelegramIdentityRepository identities,
            AccessProperties properties,
            TransactionTemplate transactions
    ) {
        this.accounts = accounts;
        this.identities = identities;
        this.properties = properties;
        this.transactions = transactions;
    }

    /**
     * @param registered аккаунт завели прямо сейчас — значит, человек у бота впервые
     */
    public record Resolved(AccountAccess account, boolean registered) {
    }

    public Resolved resolveAccount(long telegramId, String displayName, String username) {
        try {
            return transactions.execute(status -> findOrRegister(telegramId, displayName, username));
        } catch (ConcurrentRegistrationException exception) {
            // Первое и второе сообщения нового пользователя пришли одновременно — привязку уже создал соседний запрос.
            return transactions.execute(status -> findOrRegister(telegramId, displayName, username));
        }
    }

    private Resolved findOrRegister(long telegramId, String displayName, String username) {
        boolean registered = identities.findById(telegramId).isEmpty();
        TelegramIdentityEntity identity = registered
                ? register(telegramId, displayName, username)
                : identities.findById(telegramId).orElseThrow();
        if (!registered && identity.rename(displayName, username)) {
            identities.saveAndFlush(identity);
        }
        AccountEntity account = accounts.findById(identity.getAccountId()).orElseThrow();
        // Первый администратор приходит из конфигурации: внутри бота выдать себе права некому.
        if (properties.isConfiguredAdmin(telegramId)
                && (account.getRole() != AccountRole.ADMIN || account.getAccess() != AccessState.ALLOWED)) {
            account.setRole(AccountRole.ADMIN);
            account.setAccess(AccessState.ALLOWED);
            accounts.saveAndFlush(account);
        }
        AccountAccess access = new AccountAccess(account.getId(), telegramId, identity.title(),
                account.getRole(), account.getAccess());
        return new Resolved(access, registered);
    }

    private TelegramIdentityEntity register(long telegramId, String displayName, String username) {
        UUID accountId = UUID.randomUUID();
        accounts.saveAndFlush(new AccountEntity(accountId, AccountStatus.ACTIVE, AccountRole.USER, AccessState.PENDING));
        if (identities.insertIfAbsent(telegramId, accountId, displayName, username) == 0) {
            throw new ConcurrentRegistrationException();
        }
        return identities.findById(telegramId).orElseThrow();
    }

    static final class ConcurrentRegistrationException extends RuntimeException {

        ConcurrentRegistrationException() {
            super("Пользователь Telegram уже привязан параллельным запросом");
        }
    }
}
