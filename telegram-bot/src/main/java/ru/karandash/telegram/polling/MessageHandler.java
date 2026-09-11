package ru.karandash.telegram.polling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.karandash.contracts.telegram.TelegramInboundMessage;
import ru.karandash.contracts.telegram.TelegramReply;
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
 * Одно входящее сообщение: личный чат → фото или текст в ядро → ответ пользователю.
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
            reply = core.submit(new TelegramInboundMessage(update.updateId(), message.from().id(), text), photo);
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
        reply.messages().forEach(text -> telegram.sendMessage(chatId, text));
        log.info("Апдейт {} обработан: {} ({} сообщ.)", update.updateId(),
                message.hasPhoto() ? "фото" : "текст", reply.messages().size());
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
