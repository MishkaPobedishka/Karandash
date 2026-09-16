package ru.karandash.core.account;

import java.util.UUID;

/**
 * Кто написал боту: аккаунт, как его называть, роль и право пользоваться ботом.
 */
public record AccountAccess(UUID accountId, long telegramId, String title, AccountRole role, AccessState access) {

    public boolean allowed() {
        return access == AccessState.ALLOWED;
    }

    public boolean admin() {
        return role == AccountRole.ADMIN && allowed();
    }

    public boolean waiting() {
        return access == AccessState.REQUESTED;
    }

    /** Как называть человека в сообщениях: имя, если оно есть, иначе номер. */
    public String name() {
        return title == null || title.isBlank() ? String.valueOf(telegramId) : title;
    }
}
