package ru.karandash.core.account;

/**
 * Итог решения по заявке.
 *
 * @param applied    {@code false} — кто-то из администраторов успел решить раньше
 * @param decidedBy  имя администратора, чьё решение в силе
 */
public record AccessDecision(boolean applied, AccountAccess account, String decidedBy) {
}
