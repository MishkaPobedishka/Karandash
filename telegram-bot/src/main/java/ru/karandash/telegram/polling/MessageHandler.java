package ru.karandash.telegram.polling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.karandash.contracts.telegram.ReplyPhoto;
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

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
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
    /** Через сколько молчания сказать человеку, что бот занят: короткие ответы успевают раньше. */
    private static final Duration DEFAULT_NOTICE_DELAY = Duration.ofSeconds(3);

    private final TelegramApiClient telegram;
    private final CoreClient core;
    private final ScheduledExecutorService typingScheduler;
    private final long maxPhotoBytes;
    private final Duration noticeDelay;

    public MessageHandler(
            TelegramApiClient telegram,
            CoreClient core,
            ScheduledExecutorService typingScheduler,
            long maxPhotoBytes
    ) {
        this(telegram, core, typingScheduler, maxPhotoBytes, DEFAULT_NOTICE_DELAY);
    }

    MessageHandler(
            TelegramApiClient telegram,
            CoreClient core,
            ScheduledExecutorService typingScheduler,
            long maxPhotoBytes,
            Duration noticeDelay
    ) {
        this.telegram = telegram;
        this.core = core;
        this.typingScheduler = typingScheduler;
        this.maxPhotoBytes = maxPhotoBytes;
        this.noticeDelay = noticeDelay;
    }

    /**
     * Подсказка «секунду, считаю…»: уходит, если ядро думает дольше нескольких секунд,
     * и убирается, как только пришёл настоящий ответ. Без неё человек видит только «печатает…».
     */
    private Waiting startWaiting(long chatId) {
        AtomicLong noticeId = new AtomicLong();
        ScheduledFuture<?> typing = startTyping(chatId);
        ScheduledFuture<?> notice = typingScheduler.schedule(() -> {
            try {
                Long sent = telegram.sendMessage(chatId, BotTexts.WORKING);
                if (sent != null) {
                    noticeId.set(sent);
                }
            } catch (RuntimeException exception) {
                log.debug("Подсказку об ожидании отправить не удалось");
            }
        }, noticeDelay.toMillis(), TimeUnit.MILLISECONDS);
        return new Waiting(chatId, typing, notice, noticeId);
    }

    /** Индикатор набора, подсказка об ожидании и её уборка — одним объектом, чтобы не забыть остановить. */
    private final class Waiting {

        private final long chatId;
        private final ScheduledFuture<?> typing;
        private final ScheduledFuture<?> notice;
        private final AtomicLong noticeId;

        private Waiting(long chatId, ScheduledFuture<?> typing, ScheduledFuture<?> notice, AtomicLong noticeId) {
            this.chatId = chatId;
            this.typing = typing;
            this.notice = notice;
            this.noticeId = noticeId;
        }

        private void stop() {
            typing.cancel(false);
            notice.cancel(false);
            long sent = noticeId.get();
            if (sent == 0) {
                return;
            }
            try {
                telegram.deleteMessage(chatId, sent);
            } catch (RuntimeException exception) {
                // Сообщение могли удалить раньше — на ответ пользователю это не влияет.
                log.debug("Подсказку об ожидании убрать не удалось");
            }
        }
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
        Waiting waiting = startWaiting(chatId);
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
            waiting.stop();
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
        Waiting waiting = startWaiting(chatId);
        TelegramReply reply;
        try {
            reply = core.submit(TelegramInboundMessage.button(update.updateId(), callback.from().id(),
                    callback.from().displayName(), callback.from().username(), callback.data()), null);
        } catch (CoreUnavailableException exception) {
            log.warn("Апдейт {}: {}", update.updateId(), exception.getMessage());
            telegram.sendMessage(chatId, BotTexts.CORE_UNAVAILABLE);
            return;
        } finally {
            waiting.stop();
        }
        if (reply.duplicate()) {
            log.info("Апдейт {} уже обработан ядром — повторный ответ не отправляется", update.updateId());
            return;
        }
        Long messageId = callback.message() == null ? null : callback.message().messageId();
        if (reply.replaceMessage() && messageId != null && reply.messages().size() == 1
                && telegram.editMessage(chatId, messageId, reply.messages().getFirst(), reply.buttons())) {
            log.info("Апдейт {} обработан: экран заменён на месте", update.updateId());
            return;
        }
        if (messageId != null) {
            // Кнопки прошлого сообщения больше не нужны: ответ уходит новым сообщением.
            clearButtonsQuietly(chatId, messageId);
        }
        send(chatId, reply);
        log.info("Апдейт {} обработан: кнопка ({} сообщ.)", update.updateId(), reply.messages().size());
    }

    /**
     * Кнопки ядро присылает к последнему сообщению ответа. Фотографии уходят следом альбомом:
     * Telegram забирает их по ссылкам сам, и ждать этого, прежде чем показать текст, незачем.
     */
    private void send(long chatId, TelegramReply reply) {
        List<String> messages = reply.messages();
        for (int index = 0; index < messages.size(); index++) {
            boolean last = index == messages.size() - 1;
            telegram.sendMessage(chatId, messages.get(index), last ? reply.buttons() : List.of());
        }
        sendPhotosQuietly(chatId, reply.photos());
    }

    private void sendPhotosQuietly(long chatId, List<ReplyPhoto> photos) {
        if (photos.isEmpty()) {
            return;
        }
        try {
            telegram.sendPhotos(chatId, photos);
        } catch (RuntimeException exception) {
            // Ссылка столовой могла протухнуть — текст с подбором важнее картинок, его всё равно отправим.
            log.info("Фотографии блюд не отправлены: {}", exception.getMessage());
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
