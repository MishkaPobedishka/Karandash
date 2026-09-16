package ru.karandash.core.changelog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.karandash.core.account.AccessService;
import ru.karandash.core.account.AccountAccess;
import ru.karandash.core.telegram.TelegramNotifications;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * «Что нового» для пользователей: администратор пишет запись и рассылает её всем, у кого есть доступ.
 * Рассылка идёт через outbox — если Telegram недоступен, сообщения не теряются.
 */
@Service
public class ChangelogService {

    private static final Logger log = LoggerFactory.getLogger(ChangelogService.class);
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_BODY_LENGTH = 3000;

    private final ChangelogRepository entries;
    private final AccessService access;
    private final TelegramNotifications notifications;

    public ChangelogService(
            ChangelogRepository entries,
            AccessService access,
            TelegramNotifications notifications
    ) {
        this.entries = entries;
        this.access = access;
        this.notifications = notifications;
    }

    /** Первая строка текста — заголовок, остальное — тело записи. */
    @Transactional
    public ChangelogEntity create(String text, UUID authorAccountId) {
        String[] lines = text.strip().split("\\R", 2);
        String title = limit(lines[0].strip(), MAX_TITLE_LENGTH);
        String body = lines.length > 1 ? limit(lines[1].strip(), MAX_BODY_LENGTH) : "";
        if (title.isEmpty()) {
            throw new IllegalArgumentException("Запись без заголовка");
        }
        return entries.saveAndFlush(new ChangelogEntity(title, body, authorAccountId));
    }

    @Transactional(readOnly = true)
    public Optional<ChangelogEntity> find(UUID id) {
        return entries.findById(id);
    }

    @Transactional(readOnly = true)
    public List<ChangelogEntity> published(int limit) {
        return entries.findByPublishedAtNotNullOrderByPublishedAtDesc(Limit.of(limit));
    }

    @Transactional(readOnly = true)
    public List<ChangelogEntity> drafts() {
        return entries.findByPublishedAtNullOrderByCreatedAt();
    }

    @Transactional
    public void delete(UUID id) {
        entries.deleteById(id);
    }

    /**
     * Рассылает запись всем, у кого открыт доступ, и помечает её опубликованной.
     *
     * @return сколько человек получит сообщение
     */
    @Transactional
    public int publish(UUID id) {
        ChangelogEntity entry = entries.findById(id).orElseThrow(() -> new IllegalArgumentException("Записи нет"));
        if (entry.getPublishedAt() != null) {
            throw new IllegalStateException("Запись уже разослана");
        }
        List<AccountAccess> recipients = access.allowed();
        String text = message(entry);
        for (AccountAccess recipient : recipients) {
            notifications.notify(recipient.accountId(), recipient.telegramId(), text);
        }
        entry.publish(recipients.size());
        entries.saveAndFlush(entry);
        log.info("Запись «что нового» разослана: получателей {}", recipients.size());
        return recipients.size();
    }

    public static String message(ChangelogEntity entry) {
        String text = "Что нового\n\n" + entry.getTitle();
        return entry.getBody().isBlank() ? text : text + "\n\n" + entry.getBody();
    }

    private static String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
    }
}
