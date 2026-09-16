package ru.karandash.telegram.polling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.karandash.contracts.telegram.TelegramInboundMessage;
import ru.karandash.contracts.telegram.TelegramReply;
import ru.karandash.telegram.api.CallbackQuery;
import ru.karandash.telegram.api.Message;
import ru.karandash.telegram.api.PhotoSize;
import ru.karandash.telegram.api.TelegramApiClient;
import ru.karandash.telegram.api.TelegramApiException;
import ru.karandash.telegram.api.TelegramFile;
import ru.karandash.telegram.api.Update;
import ru.karandash.telegram.core.CoreClient;
import ru.karandash.telegram.core.CoreUnavailableException;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Один входящий апдейт: личный чат → фото, текст или нажатие кнопки в ядро → ответ пользователю.
 * В логи попадают только номер апдейта и исход — ни текста, ни фото, ни идентификатора пользователя.
 */
public class MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(MessageHandler.class);
    /** Фото крупнее этого по длинной стороне модель всё равно уменьшит — лишние байты и токены. */
    private static final int PREFERRED_PHOTO_SIDE = 1600;
    private static final long TYPING_REFRESH_SECONDS = 4;

    private final TelegramApiClient telegram;
    private final CoreClient core;
    private final ScheduledExecutorService typingScheduler;
    private final long maxPhotoBytes;

    public MessageHandler(
            TelegramApiClient telegram,
            CoreClient core,
            ScheduledExecutorService typingScheduler,
            long maxPhotoBytes
    ) {
        this.telegram = telegram;
        this.core = core;
        this.typingScheduler = typingScheduler;
        this.maxPhotoBytes = maxPhotoBytes;
    }

    /** Обрабатывает апдейт и никогда не бросает: сбой одного сообщения не должен останавливать приём. */
    public void handleSafely(Update update) {
        try {
            handle(update);
        } catch (TelegramApiException exception) {
            log.warn("Апдейт {}: ответ пользователю не отправлен — {}", update.updateId(), exception.getMessage());
        } catch (RuntimeException exception) {
            log.warn("Апдейт {} не обработан: {}", update.updateId(), exception.getClass().getSimpleName());
        }
    }

    void handle(Update update) {
        if (update.callbackQuery() != null) {
            handleButton(update, update.callbackQuery());
            return;
        }
        Message message = update.message();
        if (message == null || message.chat() == null || !message.chat().isPrivate()
                || message.from() == null || message.from().bot()) {
            log.debug("Апдейт {} пропущен: не личное сообщение от пользователя", update.updateId());
            return;
        }
        long chatId = message.chat().id();
        if (!message.hasPhoto() && !message.hasText()) {
            telegram.sendMessage(chatId, BotTexts.UNSUPPORTED);
            return;
        }
        ScheduledFuture<?> typing = startTyping(chatId);
        TelegramReply reply;
        try {
            byte[] photo = message.hasPhoto() ? downloadPhoto(message.photo()) : null;
            String text = message.hasPhoto() ? message.caption() : message.text();
            reply = core.submit(TelegramInboundMessage.message(update.updateId(), message.from().id(),
                    message.from().displayName(), message.from().username(), text), photo);
        } catch (TelegramApiClient.FileTooLargeException exception) {
            log.info("Апдейт {}: фото больше лимита", update.updateId());
            telegram.sendMessage(chatId, BotTexts.PHOTO_TOO_LARGE);
            return;
        } catch (TelegramApiException exception) {
            log.warn("Апдейт {}: фото не скачано — {}", update.updateId(), exception.getMessage());
            telegram.sendMessage(chatId, BotTexts.PHOTO_UNAVAILABLE);
            return;
        } catch (CoreUnavailableException exception) {
            log.warn("Апдейт {}: {}", update.updateId(), exception.getMessage());
            telegram.sendMessage(chatId, BotTexts.CORE_UNAVAILABLE);
            return;
        } finally {
            typing.cancel(false);
        }
        if (reply.duplicate()) {
            log.info("Апдейт {} уже обработан ядром — повторный ответ не отправляется", update.updateId());
            return;
        }
        send(chatId, reply);
        log.info("Апдейт {} обработан: {} ({} сообщ.)", update.updateId(),
                message.hasPhoto() ? "фото" : "текст", reply.messages().size());
    }

    /** Нажатие кнопки под прошлым ответом бота. */
    private void handleButton(Update update, CallbackQuery callback) {
        if (callback.from() == null || callback.from().bot() || callback.id() == null || callback.data() == null) {
            log.debug("Апдейт {} пропущен: нажатие без пользователя или данных", update.updateId());
            return;
        }
        if (callback.message() != null && callback.message().chat() != null
                && !callback.message().chat().isPrivate()) {
            log.debug("Апдейт {} пропущен: нажатие не в личном чате", update.updateId());
            return;
        }
        Long chatId = update.chatId();
        if (chatId == null) {
            log.debug("Апдейт {} пропущен: непонятно, куда отвечать", update.updateId());
            return;
        }
        // Часы на кнопке гасим сразу: ответ ядра вместе с моделью занимает секунды.
        answerQuietly(callback.id());
        if (callback.message() != null) {
            clearButtonsQuietly(chatId, callback.message().messageId());
        }
        ScheduledFuture<?> typing = startTyping(chatId);
        TelegramReply reply;
        try {
            reply = core.submit(TelegramInboundMessage.button(update.updateId(), callback.from().id(),
                    callback.from().displayName(), callback.from().username(), callback.data()), null);
        } catch (CoreUnavailableException exception) {
            log.warn("Апдейт {}: {}", update.updateId(), exception.getMessage());
            telegram.sendMessage(chatId, BotTexts.CORE_UNAVAILABLE);
            return;
        } finally {
            typing.cancel(false);
        }
        if (reply.duplicate()) {
            log.info("Апдейт {} уже обработан ядром — повторный ответ не отправляется", update.updateId());
            return;
        }
        send(chatId, reply);
        log.info("Апдейт {} обработан: кнопка ({} сообщ.)", update.updateId(), reply.messages().size());
    }

    /** Кнопки ядро присылает к последнему сообщению ответа. */
    private void send(long chatId, TelegramReply reply) {
        List<String> messages = reply.messages();
        for (int index = 0; index < messages.size(); index++) {
            boolean last = index == messages.size() - 1;
            telegram.sendMessage(chatId, messages.get(index), last ? reply.buttons() : List.of());
        }
    }

    private void answerQuietly(String callbackQueryId) {
        try {
            telegram.answerCallbackQuery(callbackQueryId);
        } catch (RuntimeException exception) {
            // Нажатие уже протухло — на дальнейшую обработку это не влияет.
            log.debug("Не удалось подтвердить нажатие кнопки");
        }
    }

    private void clearButtonsQuietly(long chatId, long messageId) {
        try {
            telegram.clearButtons(chatId, messageId);
        } catch (RuntimeException exception) {
            // Сообщение могли удалить или отредактировать — защита от второго нажатия всё равно есть в ядре.
            log.debug("Не удалось убрать кнопки у прошлого сообщения");
        }
    }

    private byte[] downloadPhoto(List<PhotoSize> sizes) {
        PhotoSize size = choosePhotoSize(sizes, maxPhotoBytes);
        if (size == null) {
            throw new TelegramApiClient.FileTooLargeException();
        }
        TelegramFile file = telegram.getFile(size.fileId());
        if (file.fileSize() != null && file.fileSize() > maxPhotoBytes) {
            throw new TelegramApiClient.FileTooLargeException();
        }
        return telegram.downloadFile(file.filePath(), maxPhotoBytes);
    }

    /** Самое крупное фото до 1600 пикселей по длинной стороне; если таких нет — самое маленькое в лимите. */
    static PhotoSize choosePhotoSize(List<PhotoSize> sizes, long maxBytes) {
        List<PhotoSize> fitting = sizes.stream()
                .filter(size -> size.fileSize() == null || size.fileSize() <= maxBytes)
                .toList();
        Comparator<PhotoSize> byArea = Comparator.comparingLong(size -> (long) size.width() * size.height());
        return fitting.stream()
                .filter(size -> Math.max(size.width(), size.height()) <= PREFERRED_PHOTO_SIDE)
                .max(byArea)
                .or(() -> fitting.stream().min(byArea))
                .orElse(null);
    }

    private ScheduledFuture<?> startTyping(long chatId) {
        return typingScheduler.scheduleAtFixedRate(() -> {
            try {
                telegram.sendChatAction(chatId, "typing");
            } catch (RuntimeException ignored) {
                // индикатор набора — необязательная любезность
            }
        }, 0, TYPING_REFRESH_SECONDS, TimeUnit.SECONDS);
    }
}
