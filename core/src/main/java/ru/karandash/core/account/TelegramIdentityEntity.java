package ru.karandash.core.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "telegram_identity")
public class TelegramIdentityEntity {

    @Id
    private Long telegramId;

    @Column(nullable = false)
    private UUID accountId;

    /** Имя из Telegram — чтобы администратор видел, кого пускает, а пользователь знал, кто его пустил. */
    private String displayName;

    private String username;

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected TelegramIdentityEntity() {
    }

    public Long getTelegramId() {
        return telegramId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUsername() {
        return username;
    }

    /**
     * Имя меняется на стороне Telegram — обновляем, когда человек пишет боту.
     * Пустое значение не затирает известное: в части событий Telegram имени не присылает.
     */
    boolean rename(String displayName, String username) {
        String name = blank(displayName) ? this.displayName : displayName;
        String handle = blank(username) ? this.username : username;
        boolean changed = !Objects.equals(this.displayName, name) || !Objects.equals(this.username, handle);
        if (changed) {
            this.displayName = name;
            this.username = handle;
        }
        return changed;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /** Как называть человека в сообщениях: имя, «собачка» или номер. */
    public String title() {
        if (displayName != null && !displayName.isBlank()) {
            return username == null || username.isBlank()
                    ? displayName
                    : displayName + " (@" + username + ")";
        }
        return username == null || username.isBlank() ? String.valueOf(telegramId) : "@" + username;
    }
}
