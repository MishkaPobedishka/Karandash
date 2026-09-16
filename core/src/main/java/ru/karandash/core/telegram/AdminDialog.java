package ru.karandash.core.telegram;

import org.springframework.stereotype.Component;
import ru.karandash.contracts.telegram.TelegramReply;
import ru.karandash.core.account.AccessDecision;
import ru.karandash.core.account.AccessService;
import ru.karandash.core.account.AccountAccess;
import ru.karandash.core.changelog.ChangelogEntity;
import ru.karandash.core.changelog.ChangelogService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Управление ботом: заявки на доступ и рассылка «что нового».
 * Заявку видят все администраторы, в силе остаётся решение того, кто нажал первым;
 * остальные узнают, кто закрыл заявку.
 */
@Component
class AdminDialog {

    private static final int PUBLISHED_TO_SHOW = 5;

    private final AccessService access;
    private final ChangelogService changelog;
    private final TelegramNotifications notifications;

    AdminDialog(AccessService access, ChangelogService changelog, TelegramNotifications notifications) {
        this.access = access;
        this.changelog = changelog;
        this.notifications = notifications;
    }

    /** Пользователь подал заявку — рассылаем её всем администраторам с кнопками решения. */
    TelegramReply request(AccountAccess applicant) {
        access.request(applicant.telegramId());
        String text = TelegramTexts.ACCESS_REQUEST.formatted(applicant.name(), applicant.telegramId());
        List<ru.karandash.contracts.telegram.ReplyButton> buttons = List.of(
                new DialogCallback(DialogCallback.Action.ACCESS_GRANT, String.valueOf(applicant.telegramId()))
                        .button(TelegramTexts.BUTTON_ACCESS_GRANT),
                new DialogCallback(DialogCallback.Action.ACCESS_DENY, String.valueOf(applicant.telegramId()))
                        .button(TelegramTexts.BUTTON_ACCESS_DENY));
        access.admins().forEach(admin ->
                notifications.notify(admin.accountId(), admin.telegramId(), text, buttons));
        return TelegramReply.of(TelegramTexts.ACCESS_REQUESTED);
    }

    /** Решение по заявке: первое побеждает, остальным отвечаем, кто успел раньше. */
    TelegramReply decide(AccountAccess admin, long telegramId, boolean allow) {
        Optional<AccessDecision> decision = access.decide(telegramId, admin.accountId(), allow);
        if (decision.isEmpty()) {
            return TelegramReply.of(TelegramTexts.ACCESS_UNKNOWN_USER);
        }
        AccessDecision result = decision.get();
        if (!result.applied()) {
            return TelegramReply.of(TelegramTexts.ACCESS_ALREADY_DECIDED.formatted(result.decidedBy()));
        }
        tellApplicant(result.account(), allow, admin.name());
        tellOtherAdmins(admin, result.account(), allow);
        return TelegramReply.of(allow
                ? TelegramTexts.ACCESS_DECIDED_ALLOWED.formatted(result.account().name())
                : TelegramTexts.ACCESS_DECIDED_BLOCKED.formatted(result.account().name()));
    }

    TelegramReply users() {
        List<AccountAccess> waiting = access.waiting();
        List<AccountAccess> allowed = access.allowed();
        StringBuilder text = new StringBuilder();
        if (waiting.isEmpty()) {
            text.append("Заявок нет.");
        } else {
            text.append("Ждут решения:");
            waiting.forEach(account -> text.append("\n• ").append(account.name())
                    .append(" — ").append(account.telegramId()));
        }
        text.append("\n\nС доступом:");
        if (allowed.isEmpty()) {
            text.append("\n• никого");
        } else {
            allowed.forEach(account -> text.append("\n• ").append(account.name())
                    .append(" — ").append(account.telegramId())
                    .append(account.admin() ? ", администратор" : ""));
        }
        return TelegramReply.of(text.toString());
    }

    TelegramReply grant(AccountAccess admin, long telegramId) {
        return access.grant(telegramId, admin.accountId())
                .map(granted -> {
                    notifications.notify(granted.accountId(), telegramId,
                            TelegramTexts.ACCESS_GRANTED_BY.formatted(admin.name()));
                    return TelegramReply.of(TelegramTexts.ACCESS_GRANTED_ADMIN.formatted(granted.name()));
                })
                .orElseGet(() -> TelegramReply.of(TelegramTexts.ACCESS_UNKNOWN_USER));
    }

    TelegramReply revoke(AccountAccess admin, long telegramId) {
        return access.revoke(telegramId, admin.accountId())
                .map(revoked -> {
                    notifications.notify(revoked.accountId(), telegramId, TelegramTexts.ACCESS_BLOCKED);
                    return TelegramReply.of(TelegramTexts.ACCESS_DENIED_ADMIN.formatted(revoked.name()));
                })
                .orElseGet(() -> TelegramReply.of(TelegramTexts.ACCESS_UNKNOWN_USER));
    }

    TelegramReply promote(AccountAccess admin, long telegramId) {
        return access.promote(telegramId, admin.accountId())
                .map(promoted -> {
                    notifications.notify(promoted.accountId(), telegramId,
                            TelegramTexts.ACCESS_GRANTED_BY.formatted(admin.name()));
                    return TelegramReply.of(TelegramTexts.ACCESS_PROMOTED_ADMIN.formatted(promoted.name()));
                })
                .orElseGet(() -> TelegramReply.of(TelegramTexts.ACCESS_UNKNOWN_USER));
    }

    /** Администратор пишет запись: показываем, как её увидят люди, и предлагаем разослать. */
    TelegramReply draftChangelog(String text, UUID authorAccountId) {
        ChangelogEntity entry;
        try {
            entry = changelog.create(text, authorAccountId);
        } catch (IllegalArgumentException exception) {
            return TelegramReply.of(TelegramTexts.ADMIN_HELP);
        }
        return draftReply(entry, ChangelogService.message(entry));
    }

    TelegramReply publishChangelog(UUID entryId) {
        Optional<ChangelogEntity> entry = changelog.find(entryId);
        if (entry.isEmpty()) {
            return TelegramReply.of(TelegramTexts.CHANGELOG_GONE);
        }
        if (entry.get().getPublishedAt() != null) {
            return TelegramReply.of(TelegramTexts.CHANGELOG_ALREADY_SENT);
        }
        return TelegramReply.of(TelegramTexts.CHANGELOG_SENT.formatted(changelog.publish(entryId)));
    }

    TelegramReply deleteChangelog(UUID entryId) {
        changelog.delete(entryId);
        return TelegramReply.of(TelegramTexts.CHANGELOG_DELETED);
    }

    /** Что нового — пользователю только разосланное, администратору ещё и черновик с кнопками. */
    TelegramReply changelog(boolean admin) {
        List<ChangelogEntity> published = changelog.published(PUBLISHED_TO_SHOW);
        List<ChangelogEntity> drafts = admin ? changelog.drafts() : List.of();
        if (published.isEmpty() && drafts.isEmpty()) {
            return TelegramReply.of(TelegramTexts.CHANGELOG_EMPTY);
        }
        StringBuilder text = new StringBuilder();
        published.forEach(entry -> text.append(ChangelogService.message(entry)).append("\n\n———\n\n"));
        if (drafts.isEmpty()) {
            return TelegramReply.of(text.toString().stripTrailing());
        }
        ChangelogEntity draft = drafts.getFirst();
        text.append("Черновиков: ").append(drafts.size()).append(". Первый:\n\n")
                .append(ChangelogService.message(draft));
        return draftReply(draft, text.toString());
    }

    private TelegramReply draftReply(ChangelogEntity entry, String text) {
        return TelegramReply.withButtons(text + "\n\n" + TelegramTexts.CHANGELOG_DRAFT,
                List.of(new DialogCallback(DialogCallback.Action.CHANGELOG_SEND, entry.getId())
                                .button(TelegramTexts.BUTTON_CHANGELOG_SEND),
                        new DialogCallback(DialogCallback.Action.CHANGELOG_DROP, entry.getId())
                                .button(TelegramTexts.BUTTON_CHANGELOG_DROP)));
    }

    private void tellApplicant(AccountAccess applicant, boolean allowed, String adminName) {
        notifications.notify(applicant.accountId(), applicant.telegramId(),
                allowed ? TelegramTexts.ACCESS_GRANTED_BY.formatted(adminName) : TelegramTexts.ACCESS_DENIED);
    }

    private void tellOtherAdmins(AccountAccess decider, AccountAccess applicant, boolean allowed) {
        String text = TelegramTexts.ACCESS_DECISION_INFO.formatted(applicant.name(),
                allowed ? "одобрил" : "отклонил", decider.name());
        access.admins().stream()
                .filter(admin -> admin.telegramId() != decider.telegramId())
                .forEach(admin -> notifications.notify(admin.accountId(), admin.telegramId(), text));
    }
}
