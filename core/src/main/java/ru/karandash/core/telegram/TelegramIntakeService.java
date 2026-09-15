package ru.karandash.core.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import ru.karandash.contracts.ai.ModelUsage;
import ru.karandash.contracts.ai.RecognitionContract;
import ru.karandash.contracts.ai.RecognitionResult;
import ru.karandash.contracts.telegram.TelegramInboundMessage;
import ru.karandash.contracts.telegram.TelegramReply;
import ru.karandash.core.account.TelegramAccountService;
import ru.karandash.core.ai.AiProperties;
import ru.karandash.core.ai.AiProvider;
import ru.karandash.core.ai.ModelRecognition;
import ru.karandash.core.ai.ModelUnavailableException;
import ru.karandash.core.ai.RecognitionModel;
import ru.karandash.core.diary.DiaryService;
import ru.karandash.core.diary.DraftSource;
import ru.karandash.core.diary.MealDraft;
import ru.karandash.core.diary.MealDraftService;
import ru.karandash.core.usage.UsageRecorder;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Разговор с пользователем: дубль → привязка аккаунта → команда, оценка, уточнение или кнопка.
 * Пока оценка не закрыта, обычное сообщение считается уточнением к ней, а не новой едой.
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
    private final DiaryReplyFormatter diaryFormatter;
    private final MealDraftService drafts;
    private final DiaryService diary;
    private final AiProperties aiProperties;
    private final TelegramIntakeProperties properties;

    public TelegramIntakeService(
            TelegramUpdateRepository updates,
            TelegramAccountService accounts,
            RecognitionModel recognitionModel,
            UsageRecorder usageRecorder,
            RecognitionReplyFormatter formatter,
            DiaryReplyFormatter diaryFormatter,
            MealDraftService drafts,
            DiaryService diary,
            AiProperties aiProperties,
            TelegramIntakeProperties properties
    ) {
        this.updates = updates;
        this.accounts = accounts;
        this.recognitionModel = recognitionModel;
        this.usageRecorder = usageRecorder;
        this.formatter = formatter;
        this.diaryFormatter = diaryFormatter;
        this.drafts = drafts;
        this.diary = diary;
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
            return message.isButton()
                    ? button(accountId, message.callbackData())
                    : respond(accountId, message, photo);
        } catch (RuntimeException exception) {
            // Непредвиденный сбой: освобождаем апдейт, чтобы повторная доставка обработалась заново.
            updates.deleteById(message.updateId());
            throw exception;
        }
    }

    private TelegramReply respond(UUID accountId, TelegramInboundMessage message, IncomingPhoto photo) {
        String text = message.text() == null ? "" : message.text().strip();
        if (photo != null && photo.bytes() != null && photo.bytes().length > 0) {
            // Новое фото — всегда новая еда, прошлая оценка закрывается.
            return estimate(accountId, RecognitionKind.PHOTO, DraftSource.PHOTO, null,
                    () -> recognitionModel.recognizePhotoWithUsage(photo.bytes(), photo.contentType()));
        }
        if (text.startsWith("/")) {
            return command(accountId, text);
        }
        if (text.isEmpty()) {
            return TelegramReply.of(TelegramTexts.HELP);
        }
        if (text.length() > properties.maxTextLength()) {
            return TelegramReply.of(TelegramTexts.TOO_LONG.formatted(properties.maxTextLength()));
        }
        Optional<MealDraft> active = drafts.findActive(accountId);
        if (active.isPresent()) {
            return revise(accountId, active.get(), text);
        }
        return estimate(accountId, RecognitionKind.TEXT, DraftSource.TEXT, text,
                () -> recognitionModel.recognizeTextWithUsage(text));
    }

    private TelegramReply command(UUID accountId, String text) {
        String command = text.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        int botNameStart = command.indexOf('@');
        if (botNameStart > 0) {
            command = command.substring(0, botNameStart);
        }
        return switch (command) {
            case "/start" -> TelegramReply.of(TelegramTexts.GREETING);
            case "/help" -> TelegramReply.of(TelegramTexts.HELP);
            case "/diary", "/дневник" -> TelegramReply.of(diaryFormatter.day(diary.today(accountId)));
            default -> TelegramReply.of(TelegramTexts.UNKNOWN_COMMAND);
        };
    }

    /** Нажатие кнопки под прошлой оценкой. Черновик ищется вместе с аккаунтом — чужой не открыть. */
    private TelegramReply button(UUID accountId, String callbackData) {
        Optional<DialogCallback> callback = DialogCallback.parse(callbackData);
        if (callback.isEmpty()) {
            log.info("Кнопка с неизвестными данными — отвечаем, что оценка закрыта");
            return TelegramReply.of(TelegramTexts.DRAFT_GONE);
        }
        Optional<MealDraft> draft = drafts.find(accountId, callback.get().draftId());
        if (draft.isEmpty()) {
            return TelegramReply.of(TelegramTexts.DRAFT_GONE);
        }
        return switch (callback.get().action()) {
            case SAVE -> save(accountId, draft.get());
            case DROP -> {
                drafts.discard(accountId);
                yield TelegramReply.of(TelegramTexts.DROPPED);
            }
            case AS_IS -> revise(accountId, draft.get(), RecognitionContract.NO_DETAILS_COMMENT);
        };
    }

    private TelegramReply save(UUID accountId, MealDraft draft) {
        if (draft.result().items().isEmpty()) {
            return TelegramReply.of(TelegramTexts.NOTHING_TO_SAVE);
        }
        String reply = diaryFormatter.saved(diary.record(draft));
        drafts.discard(accountId);
        log.info("Запись дневника создана: позиций {}, уточнений до записи {}",
                draft.result().items().size(), draft.revision());
        return TelegramReply.of(reply);
    }

    private TelegramReply estimate(
            UUID accountId,
            RecognitionKind kind,
            DraftSource source,
            String inputText,
            Supplier<ModelRecognition> call
    ) {
        return recognize(accountId, kind, call,
                result -> drafts.save(accountId, source, inputText, result, 0));
    }

    /** Пересчёт той же еды с учётом замечания пользователя: прошлая оценка уходит модели как контекст. */
    private TelegramReply revise(UUID accountId, MealDraft draft, String comment) {
        String request = RecognitionContract.revisionRequest(draft.inputText(), drafts.write(draft.result()), comment);
        return recognize(accountId, RecognitionKind.REVISION,
                () -> recognitionModel.recognizeTextWithUsage(request),
                result -> drafts.save(accountId, draft.source(), draft.inputText(), result, draft.revision() + 1));
    }

    private TelegramReply recognize(
            UUID accountId,
            RecognitionKind kind,
            Supplier<ModelRecognition> call,
            Function<RecognitionResult, MealDraft> store
    ) {
        long startedAt = System.nanoTime();
        try {
            ModelRecognition recognition = call.get();
            usageRecorder.record(accountId, kind.success(), providerName(), recognition.usage(), elapsed(startedAt));
            log.info("Распознавание {}: позиций {}, вопросов {}, {} мс", kind, recognition.result().items().size(),
                    recognition.result().questions().size(), elapsed(startedAt).toMillis());
            return estimateReply(store, recognition.result());
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

    /** Оценку показываем вместе с решением, которое от пользователя требуется. */
    private TelegramReply estimateReply(
            Function<RecognitionResult, MealDraft> store,
            RecognitionResult result
    ) {
        String text = formatter.format(result);
        if (result.items().isEmpty() && result.questions().isEmpty()) {
            return TelegramReply.of(text);
        }
        MealDraft draft = store.apply(result);
        if (!result.items().isEmpty()) {
            return TelegramReply.withButtons(text + "\n\n" + TelegramTexts.SAVE_QUESTION, List.of(
                    new DialogCallback(DialogCallback.Action.SAVE, draft.id()).button(TelegramTexts.BUTTON_SAVE),
                    new DialogCallback(DialogCallback.Action.DROP, draft.id()).button(TelegramTexts.BUTTON_DROP)));
        }
        return TelegramReply.withButtons(text + "\n\n" + TelegramTexts.ANSWER_OR_AS_IS, List.of(
                new DialogCallback(DialogCallback.Action.AS_IS, draft.id()).button(TelegramTexts.BUTTON_AS_IS),
                new DialogCallback(DialogCallback.Action.DROP, draft.id()).button(TelegramTexts.BUTTON_DROP)));
    }

    private String providerName() {
        return aiProperties.provider().configName();
    }

    private static Duration elapsed(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }

    private enum RecognitionKind {
        TEXT,
        PHOTO,
        REVISION;

        String success() {
            return "RECOGNITION_" + name();
        }

        String failure() {
            return "RECOGNITION_" + name() + "_FAILED";
        }
    }
}
