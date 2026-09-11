package ru.karandash.telegram.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import ru.karandash.contracts.telegram.BotNotification;
import ru.karandash.telegram.api.TelegramApiClient;
import ru.karandash.telegram.api.TelegramApiException;

import java.io.IOException;

/**
 * Асинхронные уведомления от ядра из {@code q.bot.events}. Тело события — JSON, заголовкам типов не доверяем.
 * Временные ошибки Telegram повторяются, после исчерпания попыток сообщение уходит в {@code q.bot.events.dead}.
 */
@Component
public class BotNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(BotNotificationListener.class);

    private final TelegramApiClient telegram;
    private final ObjectMapper objectMapper;

    public BotNotificationListener(TelegramApiClient telegram, ObjectMapper objectMapper) {
        this.telegram = telegram;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "q.bot.events", autoStartup = "${karandash.telegram.notifications-enabled:true}")
    public void onEvent(Message message) {
        String eventType = message.getMessageProperties().getReceivedRoutingKey();
        if (!BotNotification.EVENT_TYPE.equals(eventType)) {
            log.debug("Событие {} не для приёмщика — пропущено", eventType);
            return;
        }
        BotNotification notification;
        try {
            notification = objectMapper.readValue(message.getBody(), BotNotification.class);
        } catch (IOException exception) {
            throw new AmqpRejectAndDontRequeueException("Уведомление не разобрано");
        }
        if (notification.chatId() == 0 || notification.text() == null || notification.text().isBlank()) {
            log.warn("Уведомление без адресата или текста отброшено");
            return;
        }
        try {
            telegram.sendMessage(notification.chatId(), notification.text());
        } catch (TelegramApiException exception) {
            if (exception.isPermanent()) {
                log.warn("Уведомление не доставлено, повтор не поможет: код {}", exception.errorCode());
                return;
            }
            throw exception;
        }
    }
}
