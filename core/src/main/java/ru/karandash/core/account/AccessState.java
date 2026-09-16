package ru.karandash.core.account;

/** Пускать ли пользователя к боту. */
public enum AccessState {
    /** Написал впервые, заявку ещё не подавал. */
    PENDING,
    /** Заявка отправлена администраторам, решения пока нет. */
    REQUESTED,
    ALLOWED,
    /** Заявку отклонили или доступ забрали. */
    BLOCKED
}
