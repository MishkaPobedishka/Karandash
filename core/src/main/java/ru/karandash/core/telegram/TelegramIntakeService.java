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
import ru.karandash.contracts.telegram.TelegramUserName;
import ru.karandash.core.account.AccessService;
import ru.karandash.core.account.AccessState;
import ru.karandash.core.account.AccountAccess;
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
import ru.karandash.core.profile.ProfileService;
import ru.karandash.core.profile.ProfileSetup;
import ru.karandash.core.usage.UsageRecorder;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Разговор с пользователем: дубль → аккаунт и доступ → команда, мастер расчёта нормы, оценка еды или кнопка.
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
    private final ProfileDialog profileDialog;
    private final AdminDialog adminDialog;
    private final AccessService access;
    private final MealDraftService drafts;
    private final DiaryService diary;
    private final ProfileService profiles;
    private final AiProperties aiProperties;
    private final TelegramIntakeProperties properties;

    public TelegramIntakeService(
            TelegramUpdateRepository updates,
            TelegramAccountService accounts,
            RecognitionModel recognitionModel,
            UsageRecorder usageRecorder,
            RecognitionReplyFormatter formatter,
            DiaryReplyFormatter diaryFormatter,
            ProfileDialog profileDialog,
            AdminDialog adminDialog,
            AccessService access,
            MealDraftService drafts,
            DiaryService diary,
            ProfileService profiles,
            AiProperties aiProperties,
            TelegramIntakeProperties properties
    ) {
        this.updates = updates;
        this.accounts = accounts;
        this.recognitionModel = recognitionModel;
        this.usageRecorder = usageRecorder;
        this.formatter = formatter;
        this.diaryFormatter = diaryFormatter;
        this.profileDialog = profileDialog;
        this.adminDialog = adminDialog;
        this.access = access;
        this.drafts = drafts;
        this.diary = diary;
        this.profiles = profiles;
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
            TelegramAccountService.Resolved resolved = accounts.resolveAccount(
                    message.telegramUserId(), message.userName(), message.userHandle());
            AccountAccess account = resolved.account();
            if (!account.allowed()) {
                return locked(account, message);
            }
            return message.isButton()
                    ? button(account, message.callbackData())
                    : respond(account, message, photo);
        } catch (RuntimeException exception) {
            // Непредвиденный сбой: освобождаем апдейт, чтобы повторная доставка обработалась заново.
            updates.deleteById(message.updateId());
            throw exception;
        }
    }

    /** Имена, которые приёмщик узнал в Telegram: администратор должен видеть людей, а не номера. */
    public int rememberNames(List<TelegramUserName> names) {
        if (names == null || names.isEmpty()) {
            return 0;
        }
        int saved = access.rename(names);
        log.info("Имена пользователей обновлены: {} из {}", saved, names.size());
        return saved;
    }

    /** Бот в бета-тесте: без доступа работает только заявка. */
    private TelegramReply locked(AccountAccess account, TelegramInboundMessage message) {
        boolean wantsAccess = message.isButton()
                && DialogCallback.parse(message.callbackData())
                .filter(callback -> callback.action() == DialogCallback.Action.REQUEST_ACCESS)
                .isPresent();
        if (wantsAccess && account.access() == AccessState.PENDING) {
            log.info("Заявка на доступ отправлена администраторам");
            return adminDialog.request(account);
        }
        return switch (account.access()) {
            case BLOCKED -> TelegramReply.of(TelegramTexts.ACCESS_BLOCKED);
            case REQUESTED -> TelegramReply.of(TelegramTexts.ACCESS_WAITING);
            default -> TelegramReply.withButtons(
                    TelegramTexts.ACCESS_BETA.formatted(account.telegramId()),
                    List.of(new DialogCallback(DialogCallback.Action.REQUEST_ACCESS)
                            .button(TelegramTexts.BUTTON_REQUEST_ACCESS)));
        };
    }

    private TelegramReply respond(AccountAccess account, TelegramInboundMessage message, IncomingPhoto photo) {
        UUID accountId = account.accountId();
        String text = message.text() == null ? "" : message.text().strip();
        if (text.startsWith("/")) {
            return command(account, text);
        }
        Optional<ProfileSetup> setup = profiles.activeSetup(accountId);
        if (setup.isPresent()) {
            return profileAnswer(accountId, setup.get(), text, photo);
        }
        if (photo != null && photo.bytes() != null && photo.bytes().length > 0) {
            // Новое фото — всегда новая еда, прошлая оценка закрывается.
            return estimate(accountId, RecognitionKind.PHOTO, DraftSource.PHOTO, null,
                    () -> recognitionModel.recognizePhotoWithUsage(photo.bytes(), photo.contentType()));
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

    private TelegramReply command(AccountAccess account, String text) {
        String[] parts = text.split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        int botNameStart = command.indexOf('@');
        if (botNameStart > 0) {
            command = command.substring(0, botNameStart);
        }
        String argument = parts.length > 1 ? parts[1].strip() : "";
        return switch (command) {
            case "/start" -> TelegramReply.of(withAdminHint(account, TelegramTexts.GREETING));
            case "/help" -> TelegramReply.of(withAdminHint(account, TelegramTexts.HELP));
            case "/id", "/whoami" -> TelegramReply.of(
                    TelegramTexts.ACCESS_MY_NUMBER.formatted(account.telegramId()));
            case "/diary", "/дневник" -> TelegramReply.of(diaryFormatter.day(
                    diary.today(account.accountId()), profiles.dailyTarget(account.accountId())));
            case "/profile", "/профиль" -> profile(account.accountId());
            case "/changelog" -> changelog(account, argument);
            case "/admin" -> adminOnly(account, adminDialog::panel);
            case "/users" -> adminOnly(account, () -> adminDialog.userList(account));
            case "/grant" -> adminAction(account, command, argument,
                    telegramId -> adminDialog.grant(account, telegramId));
            case "/revoke" -> adminAction(account, command, argument,
                    telegramId -> adminDialog.revoke(account, telegramId));
            case "/promote" -> adminAction(account, command, argument,
                    telegramId -> adminDialog.promote(account, telegramId));
            default -> TelegramReply.of(TelegramTexts.UNKNOWN_COMMAND);
        };
    }

    private TelegramReply adminAction(
            AccountAccess account,
            String command,
            String argument,
            Function<Long, TelegramReply> action
    ) {
        if (!account.admin()) {
            return TelegramReply.of(TelegramTexts.ADMIN_ONLY);
        }
        try {
            return action.apply(Long.parseLong(argument.strip()));
        } catch (NumberFormatException exception) {
            return TelegramReply.of(TelegramTexts.ADMIN_NEED_NUMBER.formatted(command));
        }
    }

    private TelegramReply changelog(AccountAccess account, String argument) {
        if (account.admin() && !argument.isBlank()) {
            return adminDialog.draftChangelog(argument, account.accountId());
        }
        return adminDialog.changelog(account.admin());
    }

    /** Нажатие кнопки под прошлым ответом. Всё, что кнопка называет, проверяется по аккаунту нажавшего. */
    private TelegramReply button(AccountAccess account, String callbackData) {
        Optional<DialogCallback> parsed = DialogCallback.parse(callbackData);
        if (parsed.isEmpty()) {
            log.info("Кнопка с неизвестными данными — отвечаем, что оценка закрыта");
            return TelegramReply.of(TelegramTexts.DRAFT_GONE);
        }
        DialogCallback callback = parsed.get();
        return switch (callback.action()) {
            case SAVE, DROP, AS_IS -> mealButton(account.accountId(), callback);
            case REQUEST_ACCESS -> TelegramReply.of(TelegramTexts.HELP);
            case PROFILE_START -> profileDialog.question(profiles.startSetup(account.accountId()));
            case PROFILE_SEX, PROFILE_ACTIVITY, PROFILE_GOAL -> profileChoice(account.accountId(), callback);
            case PROFILE_CANCEL -> {
                profiles.cancelSetup(account.accountId());
                yield TelegramReply.of(TelegramTexts.PROFILE_CANCELLED);
            }
            case ADMIN_PANEL -> adminOnly(account, adminDialog::panel);
            case ADMIN_REQUESTS -> adminOnly(account, () -> adminDialog.requests(account));
            case ADMIN_USERS -> adminOnly(account, () -> adminDialog.userList(account));
            case ADMIN_CHANGELOG -> adminOnly(account, () -> adminDialog.changelog(true));
            case ADMIN_USER -> adminOnly(account, callback, adminDialog::userCard);
            case ACCESS_GRANT, ACCESS_DENY -> accessButton(account, callback);
            case ACCESS_REVOKE -> adminOnly(account, callback,
                    telegramId -> adminDialog.revoke(account, telegramId));
            case ACCESS_PROMOTE -> adminOnly(account, callback,
                    telegramId -> adminDialog.promote(account, telegramId));
            case CHANGELOG_SEND -> changelogButton(account, callback, true);
            case CHANGELOG_DROP -> changelogButton(account, callback, false);
        };
    }

    private TelegramReply mealButton(UUID accountId, DialogCallback callback) {
        Optional<MealDraft> draft = callback.uuidPayload().flatMap(id -> drafts.find(accountId, id));
        if (draft.isEmpty()) {
            return TelegramReply.of(TelegramTexts.DRAFT_GONE);
        }
        return switch (callback.action()) {
            case SAVE -> save(accountId, draft.get());
            case DROP -> {
                drafts.discard(accountId);
                yield TelegramReply.of(TelegramTexts.DROPPED);
            }
            default -> revise(accountId, draft.get(), RecognitionContract.NO_DETAILS_COMMENT);
        };
    }

    /** Администратору напоминаем про панель — остальным про неё знать незачем. */
    private static String withAdminHint(AccountAccess account, String text) {
        return account.admin() ? text + TelegramTexts.ADMIN_HINT : text;
    }

    private TelegramReply adminOnly(AccountAccess account, Supplier<TelegramReply> action) {
        return account.admin() ? action.get() : TelegramReply.of(TelegramTexts.ADMIN_ONLY);
    }

    private TelegramReply adminOnly(
            AccountAccess account,
            DialogCallback callback,
            Function<Long, TelegramReply> action
    ) {
        if (!account.admin()) {
            return TelegramReply.of(TelegramTexts.ADMIN_ONLY);
        }
        return callback.numberPayload()
                .map(action)
                .orElseGet(() -> TelegramReply.of(TelegramTexts.ACCESS_UNKNOWN_USER));
    }

    private TelegramReply accessButton(AccountAccess account, DialogCallback callback) {
        if (!account.admin()) {
            return TelegramReply.of(TelegramTexts.ADMIN_ONLY);
        }
        return callback.numberPayload()
                .map(telegramId -> adminDialog.decide(account, telegramId,
                        callback.action() == DialogCallback.Action.ACCESS_GRANT))
                .orElseGet(() -> TelegramReply.of(TelegramTexts.ACCESS_UNKNOWN_USER));
    }

    private TelegramReply changelogButton(AccountAccess account, DialogCallback callback, boolean publish) {
        if (!account.admin()) {
            return TelegramReply.of(TelegramTexts.ADMIN_ONLY);
        }
        return callback.uuidPayload()
                .map(id -> publish ? adminDialog.publishChangelog(id) : adminDialog.deleteChangelog(id))
                .orElseGet(() -> TelegramReply.of(TelegramTexts.CHANGELOG_GONE));
    }

    // Норма калорий

    private TelegramReply profile(UUID accountId) {
        return profiles.current(accountId)
                .map(summary -> TelegramReply.withButtons(profileDialog.summary(summary),
                        List.of(ProfileDialog.startButton(true))))
                .orElseGet(() -> TelegramReply.withButtons(TelegramTexts.PROFILE_NOT_SET,
                        List.of(ProfileDialog.startButton(false))));
    }

    private TelegramReply profileAnswer(UUID accountId, ProfileSetup setup, String text, IncomingPhoto photo) {
        if (photo != null || text.isEmpty()) {
            return withQuestion(TelegramTexts.PROFILE_BUSY, setup);
        }
        Optional<ProfileSetup> answered = profileDialog.applyText(setup, text);
        if (answered.isEmpty()) {
            return withQuestion(TelegramTexts.PROFILE_BAD_ANSWER, setup);
        }
        return continueSetup(accountId, answered.get());
    }

    private TelegramReply profileChoice(UUID accountId, DialogCallback callback) {
        Optional<ProfileSetup> setup = profiles.activeSetup(accountId);
        if (setup.isEmpty()) {
            return profile(accountId);
        }
        return profileDialog.applyChoice(setup.get(), callback)
                .map(answered -> continueSetup(accountId, answered))
                .orElseGet(() -> profileDialog.question(setup.get()));
    }

    private TelegramReply continueSetup(UUID accountId, ProfileSetup setup) {
        if (!profileDialog.finished(setup)) {
            return profileDialog.question(profiles.saveSetup(accountId, setup));
        }
        log.info("Норма калорий посчитана по формуле Миффлина — Сан Жеора");
        return TelegramReply.of(profileDialog.summary(profiles.complete(accountId, setup.answers())));
    }

    private TelegramReply withQuestion(String note, ProfileSetup setup) {
        TelegramReply question = profileDialog.question(setup);
        return new TelegramReply(false, List.of(note + "\n\n" + question.messages().getFirst()), question.buttons());
    }

    // Еда

    private TelegramReply save(UUID accountId, MealDraft draft) {
        if (draft.result().items().isEmpty()) {
            return TelegramReply.of(TelegramTexts.NOTHING_TO_SAVE);
        }
        String reply = diaryFormatter.saved(diary.record(draft), profiles.dailyTarget(accountId));
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
        return recognize(accountId, kind, call, result -> drafts.save(accountId, source, inputText, result, 0));
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
    private TelegramReply estimateReply(Function<RecognitionResult, MealDraft> store, RecognitionResult result) {
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
