package ru.karandash.telegram.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import ru.karandash.contracts.telegram.TelegramNameRequest;
import ru.karandash.contracts.telegram.TelegramUserName;
import ru.karandash.telegram.api.Chat;
import ru.karandash.telegram.api.TelegramApiClient;
import ru.karandash.telegram.api.TelegramApiException;
import ru.karandash.telegram.core.CoreClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ядро не ходит в Telegram, поэтому имена людей узнаёт приёмщик: по просьбе ядра спрашивает
 * {@code getChat} и возвращает, как человека зовут. Нужно, чтобы администратор видел людей, а не номера.
 */
@Component
public class NameRequestListener {

    private static final Logger log = LoggerFactory.getLogger(NameRequestListener.class);

    private final TelegramApiClient telegram;
    private final CoreClient core;
    private final ObjectMapper objectMapper;

    public NameRequestListener(TelegramApiClient telegram, CoreClient core, ObjectMapper objectMapper) {
        this.telegram = telegram;
        this.core = core;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "q.bot.events", autoStartup = "${karandash.telegram.notifications-enabled:true}")
    public void onEvent(Message message) {
        if (!TelegramNameRequest.EVENT_TYPE.equals(message.getMessageProperties().getReceivedRoutingKey())) {
            return;
        }
        TelegramNameRequest request;
        try {
            request = objectMapper.readValue(message.getBody(), TelegramNameRequest.class);
        } catch (IOException exception) {
            throw new AmqpRejectAndDontRequeueException("Просьба об именах не разобрана");
        }
        List<TelegramUserName> names = new ArrayList<>();
        for (Long telegramId : request.telegramIds()) {
            name(telegramId).ifPresent(names::add);
        }
        if (names.isEmpty()) {
            log.info("Имена не узнались: запрошено {}", request.telegramIds().size());
            return;
        }
        core.submitNames(names);
        log.info("Имена отправлены ядру: {} из {}", names.size(), request.telegramIds().size());
    }

    private Optional<TelegramUserName> name(long telegramId) {
        try {
            Chat chat = telegram.getChat(telegramId);
            if (chat == null || (chat.displayName() == null && chat.username() == null)) {
                return Optional.empty();
            }
            return Optional.of(new TelegramUserName(telegramId, chat.displayName(), chat.username()));
        } catch (TelegramApiException exception) {
            // Человек мог удалить чат или заблокировать бота — это не повод ронять всю пачку.
            log.debug("Имя пользователя не узнать: {}", exception.getMessage());
            return Optional.empty();
        }
    }
}
