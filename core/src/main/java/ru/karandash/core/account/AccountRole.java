package ru.karandash.core.account;

/** Что пользователю позволено, кроме собственного дневника. */
public enum AccountRole {
    USER,
    /** Выдаёт доступы, назначает других администраторов и рассылает «что нового». */
    ADMIN
}
