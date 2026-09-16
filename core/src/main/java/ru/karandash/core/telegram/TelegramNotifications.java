package ru.karandash.core.telegram;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.karandash.contracts.telegram.BotNotification;
import ru.karandash.contracts.telegram.ReplyButton;
import ru.karandash.contracts.telegram.TelegramNameRequest;
import ru.karandash.core.outbox.OutboxService;

import java.util.List;
import java.util.UUID;

/**
 * Асинхронные сообщения пользователю: событие пишется в outbox в транзакции вызывающего кода,
 * публикуется в {@code core.events} и доходит до приёмщика через {@code q.bot.events}.
 */
@Service
public class TelegramNotifications {

    private final OutboxService outboxService;

    public TelegramNotifications(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Transactional
    public UUID notify(UUID accountId, long chatId, String text) {
        return notify(accountId, chatId, text, List.of());
    }

    /** Просьба к приёмщику узнать в Telegram, как зовут этих людей: у ядра доступа к Telegram нет. */
    @Transactional
    public UUID requestNames(UUID accountId, List<Long> telegramIds) {
        return outboxService.append("account", accountId, TelegramNameRequest.EVENT_TYPE,
                new TelegramNameRequest(telegramIds));
    }

    @Transactional
    public UUID notify(UUID accountId, long chatId, String text, List<ReplyButton> buttons) {
        return outboxService.append("account", accountId, BotNotification.EVENT_TYPE,
                new BotNotification(chatId, text, buttons));
    }
}
