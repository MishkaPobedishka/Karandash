package ru.karandash.core.account;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @param admins идентификаторы Telegram, которые становятся администраторами при первом сообщении.
 *               Так в системе появляется первый администратор: изнутри бота выдать себе права некому.
 */
@ConfigurationProperties(prefix = "karandash.access")
public record AccessProperties(List<Long> admins) {

    public AccessProperties {
        admins = admins == null ? List.of() : List.copyOf(admins);
    }

    public boolean isConfiguredAdmin(long telegramId) {
        return admins.contains(telegramId);
    }
}
