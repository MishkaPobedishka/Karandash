package ru.karandash.core.account;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Кому открыт бот и кто им управляет. Доступ выдаётся по заявке: её видят все администраторы,
 * а в силе остаётся решение того, кто нажал первым.
 */
@Service
public class AccessService {

    private final AccountRepository accounts;
    private final TelegramIdentityRepository identities;

    public AccessService(AccountRepository accounts, TelegramIdentityRepository identities) {
        this.accounts = accounts;
        this.identities = identities;
    }

    @Transactional(readOnly = true)
    public Optional<AccountAccess> find(long telegramId) {
        return accounts.findAccessByTelegramId(telegramId).map(AccessService::toAccess);
    }

    @Transactional(readOnly = true)
    public List<AccountAccess> waiting() {
        return accounts.findAccessByState(AccessState.REQUESTED.name()).stream().map(AccessService::toAccess).toList();
    }

    @Transactional(readOnly = true)
    public List<AccountAccess> allowed() {
        return accounts.findAllowed().stream().map(AccessService::toAccess).toList();
    }

    @Transactional(readOnly = true)
    public List<AccountAccess> admins() {
        return accounts.findAdmins().stream().map(AccessService::toAccess).toList();
    }

    /** Пользователь подал заявку. Повторное нажатие ничего не ломает: состояние то же. */
    @Transactional
    public Optional<AccountAccess> request(long telegramId) {
        return change(telegramId, account -> {
            if (account.getAccess() == AccessState.PENDING) {
                account.setAccess(AccessState.REQUESTED);
            }
        });
    }

    /**
     * Решение администратора по заявке. Если заявку уже закрыл другой администратор,
     * ответ говорит, кто именно, и ничего не меняет.
     */
    @Transactional
    public Optional<AccessDecision> decide(long telegramId, UUID adminAccountId, boolean allow) {
        Optional<TelegramIdentityEntity> identity = identities.findById(telegramId);
        if (identity.isEmpty()) {
            return Optional.empty();
        }
        UUID accountId = identity.get().getAccountId();
        AccessState state = allow ? AccessState.ALLOWED : AccessState.BLOCKED;
        boolean applied = accounts.decideRequest(accountId, state.name(), adminAccountId) == 1;
        AccountAccess account = accounts.findAccessByAccountId(accountId).map(AccessService::toAccess).orElseThrow();
        UUID decidedBy = accounts.findById(accountId).map(AccountEntity::getDecidedBy).orElse(adminAccountId);
        return Optional.of(new AccessDecision(applied, account, titleOf(decidedBy)));
    }

    /** Ручная выдача доступа администратором — по номеру, без заявки. */
    @Transactional
    public Optional<AccountAccess> grant(long telegramId, UUID adminAccountId) {
        return change(telegramId, account -> {
            account.setAccess(AccessState.ALLOWED);
            account.setDecidedBy(adminAccountId);
        });
    }

    @Transactional
    public Optional<AccountAccess> revoke(long telegramId, UUID adminAccountId) {
        return change(telegramId, account -> {
            account.setAccess(AccessState.BLOCKED);
            account.setDecidedBy(adminAccountId);
            // Забрали доступ — забираем и права: иначе останется администратор, который не может писать боту.
            account.setRole(AccountRole.USER);
        });
    }

    @Transactional
    public Optional<AccountAccess> promote(long telegramId, UUID adminAccountId) {
        return change(telegramId, account -> {
            account.setRole(AccountRole.ADMIN);
            account.setAccess(AccessState.ALLOWED);
            account.setDecidedBy(adminAccountId);
        });
    }

    /** Как зовут владельца аккаунта. */
    @Transactional(readOnly = true)
    public String titleOf(UUID accountId) {
        return accountId == null
                ? "администратор"
                : identities.findByAccountId(accountId).map(TelegramIdentityEntity::title).orElse("администратор");
    }

    private Optional<AccountAccess> change(long telegramId, Consumer<AccountEntity> change) {
        return identities.findById(telegramId)
                .flatMap(identity -> accounts.findById(identity.getAccountId())
                        .map(account -> {
                            change.accept(account);
                            accounts.saveAndFlush(account);
                            return new AccountAccess(account.getId(), telegramId, identity.title(),
                                    account.getRole(), account.getAccess());
                        }));
    }

    private static AccountAccess toAccess(AccountRepository.AccessRow row) {
        String title = title(row.getDisplayName(), row.getUsername(), row.getTelegramId());
        return new AccountAccess(row.getAccountId(), row.getTelegramId(), title,
                AccountRole.valueOf(row.getRole()), AccessState.valueOf(row.getAccess()));
    }

    private static String title(String displayName, String username, long telegramId) {
        if (displayName != null && !displayName.isBlank()) {
            return username == null || username.isBlank() ? displayName : displayName + " (@" + username + ")";
        }
        return username == null || username.isBlank() ? String.valueOf(telegramId) : "@" + username;
    }
}
