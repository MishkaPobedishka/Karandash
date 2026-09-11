package ru.karandash.core.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import ru.karandash.contracts.ai.ModelUsage;
import ru.karandash.contracts.telegram.TelegramInboundMessage;
import ru.karandash.contracts.telegram.TelegramReply;
import ru.karandash.core.account.TelegramAccountService;
import ru.karandash.core.ai.AiProperties;
import ru.karandash.core.ai.AiProvider;
import ru.karandash.core.ai.ModelRecognition;
import ru.karandash.core.ai.ModelUnavailableException;
import ru.karandash.core.ai.RecognitionModel;
import ru.karandash.core.usage.UsageRecorder;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Обработка входящего сообщения от приёмщика: дубль → привязка аккаунта → команда или распознавание → текст ответа.
 * В логи не пишутся ни текст сообщения, ни фото, ни ответ модели.
 */
@Service
@EnableConfigurationProperties(TelegramIntakeProperties.class)
public class TelegramIntakeService {

    private static final Logger log = LoggerFactory.getLogger(TelegramIntakeService.class);

    private final TelegramUpdateRepository updates;
    private final TelegramAccountService accounts;
    private final RecognitionModel recognitionModel;
    private final UsageRecorder usageRecorder;
    private final RecognitionReplyFormatter formatter;
    private final AiProperties aiProperties;
    private final TelegramIntakeProperties properties;

    public TelegramIntakeService(
            TelegramUpdateRepository updates,
            TelegramAccountService accounts,
            RecognitionModel recognitionModel,
            UsageRecorder usageRecorder,
            RecognitionReplyFormatter formatter,
            AiProperties aiProperties,
            TelegramIntakeProperties properties
    ) {
        this.updates = updates;
        this.accounts = accounts;
        this.recognitionModel = recognitionModel;
        this.usageRecorder = usageRecorder;
        this.formatter = formatter;
        this.aiProperties = aiProperties;
        this.properties = properties;
    }

    public TelegramReply handle(TelegramInboundMessage message, IncomingPhoto photo) {
        if (message == null || message.updateId() <= 0 || message.telegramUserId() <= 0) {
            throw new IllegalArgumentException("Некорректное входящее сообщение");
        }
        if (updates.claim(message.updateId()) == 0) {
            log.info("Апдейт {} уже обработан — повтор отброшен", message.updateId());
            return TelegramReply.duplicateUpdate();
        }
        try {
            UUID accountId = accounts.resolveAccount(message.telegramUserId());
            return respond(accountId, message, photo);
        } catch (RuntimeException exception) {
            // Непредвиденный сбой: освобождаем апдейт, чтобы повторная доставка обработалась заново.
            updates.deleteById(message.updateId());
            throw exception;
        }
    }

    private TelegramReply respond(UUID accountId, TelegramInboundMessage message, IncomingPhoto photo) {
        String text = message.text() == null ? "" : message.text().strip();
        if (photo != null && photo.bytes() != null && photo.bytes().length > 0) {
            return recognize(accountId, RecognitionKind.PHOTO,
                    () -> recognitionModel.recognizePhotoWithUsage(photo.bytes(), photo.contentType()));
        }
        if (text.startsWith("/")) {
            return command(text);
        }
        if (text.isEmpty()) {
            return TelegramReply.of(TelegramTexts.HELP);
        }
        if (text.length() > properties.maxTextLength()) {
            return TelegramReply.of(TelegramTexts.TOO_LONG.formatted(properties.maxTextLength()));
        }
        return recognize(accountId, RecognitionKind.TEXT, () -> recognitionModel.recognizeTextWithUsage(text));
    }

    private static TelegramReply command(String text) {
        String command = text.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        int botNameStart = command.indexOf('@');
        if (botNameStart > 0) {
            command = command.substring(0, botNameStart);
        }
        return switch (command) {
            case "/start" -> TelegramReply.of(TelegramTexts.GREETING);
            case "/help" -> TelegramReply.of(TelegramTexts.HELP);
            default -> TelegramReply.of(TelegramTexts.UNKNOWN_COMMAND);
        };
    }

    private TelegramReply recognize(UUID accountId, RecognitionKind kind, Supplier<ModelRecognition> call) {
        long startedAt = System.nanoTime();
        try {
            ModelRecognition recognition = call.get();
            usageRecorder.record(accountId, kind.success(), providerName(), recognition.usage(), elapsed(startedAt));
            log.info("Распознавание {}: позиций {}, вопросов {}, {} мс", kind, recognition.result().items().size(),
                    recognition.result().questions().size(), elapsed(startedAt).toMillis());
            return TelegramReply.of(formatter.format(recognition.result()));
        } catch (IllegalArgumentException exception) {
            log.info("Распознавание {} отклонено как некорректный ввод", kind);
            return TelegramReply.of(TelegramTexts.CANNOT_READ_INPUT);
        } catch (ModelUnavailableException exception) {
            if (aiProperties.provider() != AiProvider.DISABLED) {
                usageRecorder.record(accountId, kind.failure(), providerName(), ModelUsage.unknown(), elapsed(startedAt));
            }
            log.warn("Распознавание {} не удалось за {} мс: {}", kind, elapsed(startedAt).toMillis(),
                    exception.getMessage());
            return TelegramReply.of(TelegramTexts.MODEL_UNAVAILABLE);
        }
    }

    private String providerName() {
        return aiProperties.provider().configName();
    }

    private static Duration elapsed(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }

    private enum RecognitionKind {
        TEXT,
        PHOTO;

        String success() {
            return "RECOGNITION_" + name();
        }

        String failure() {
            return "RECOGNITION_" + name() + "_FAILED";
        }
    }
}
