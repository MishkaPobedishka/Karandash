package ru.karandash.agent.recognition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.karandash.agent.AgentProperties;
import ru.karandash.agent.cli.AgentCli;
import ru.karandash.agent.cli.CallDirectory;
import ru.karandash.agent.cli.CliAuthState;
import ru.karandash.agent.cli.CliCallException;
import ru.karandash.agent.cli.CliInvocation;
import ru.karandash.agent.cli.CliOutputHandler;
import ru.karandash.agent.cli.CliProcessRunner;
import ru.karandash.agent.cli.CliReply;
import ru.karandash.agent.cli.ImageType;
import ru.karandash.agent.cli.ProcessOutcome;
import ru.karandash.agent.cli.RecognitionTask;
import ru.karandash.contracts.ai.AgentLunchResponse;
import ru.karandash.contracts.ai.AgentRecognitionResponse;
import ru.karandash.contracts.ai.LunchAdvice;
import ru.karandash.contracts.ai.LunchContract;
import ru.karandash.contracts.ai.RecognitionContract;
import ru.karandash.contracts.ai.RecognitionResult;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Распознавание через CLI-агента. В логи попадают только вид запроса, длительность и итог —
 * ни текста, ни фото, ни ответа модели.
 */
@Service
public class RecognitionService {

    private static final Logger log = LoggerFactory.getLogger(RecognitionService.class);

    private final AgentCli cli;
    private final CliProcessRunner runner;
    private final CliAuthState authState;
    private final AgentProperties properties;
    private final ObjectMapper objectMapper;
    private final Semaphore permits;

    public RecognitionService(
            AgentCli cli,
            CliProcessRunner runner,
            CliAuthState authState,
            AgentProperties properties,
            ObjectMapper objectMapper
    ) {
        this.cli = cli;
        this.runner = runner;
        this.authState = authState;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.permits = new Semaphore(Math.max(1, properties.maxConcurrentCalls()), true);
    }

    public AgentRecognitionResponse recognizeText(String description) {
        if (description == null || description.isBlank()) {
            throw new InvalidInputException("Описание еды не должно быть пустым");
        }
        if (description.length() > properties.maxTextLength()) {
            throw new InvalidInputException("Описание еды длиннее " + properties.maxTextLength() + " символов");
        }
        return recognize(new RecognitionTask.Text(description.strip()));
    }

    public AgentRecognitionResponse recognizePhoto(byte[] image) {
        if (image == null || image.length == 0) {
            throw new InvalidInputException("Фотография не должна быть пустой");
        }
        if (image.length > properties.maxPhotoSize().toBytes()) {
            throw new InvalidInputException("Фотография больше " + properties.maxPhotoSize().toMegabytes() + " МБ");
        }
        ImageType type = ImageType.detect(image)
                .orElseThrow(() -> new InvalidInputException("Поддерживаются только фото JPEG, PNG, WebP и GIF"));
        return recognize(new RecognitionTask.Photo(image, type));
    }

    /** Подбор обеда: меню и рамки собирает ядро, поэтому вход здесь доверенный, а вот ответ — нет. */
    public AgentLunchResponse adviseLunch(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new InvalidInputException("Запрос на подбор обеда пустой");
        }
        if (prompt.length() > properties.maxLunchPromptLength()) {
            throw new InvalidInputException("Запрос на подбор обеда длиннее "
                    + properties.maxLunchPromptLength() + " символов");
        }
        CliReply reply = call(new RecognitionTask.Lunch(prompt));
        LunchAdvice advice = parseLunch(reply.text());
        log.info("Подбор обеда через {}: вариантов {}", cli.provider(), advice.options().size());
        return new AgentLunchResponse(advice, reply.usage());
    }

    private AgentRecognitionResponse recognize(RecognitionTask task) {
        CliReply reply = call(task);
        RecognitionResult result = parseResult(reply.text());
        log.info("Распознавание {} через {}: позиций {}, вопросов {}", task.kind(), cli.provider(),
                result.items().size(), result.questions().size());
        return new AgentRecognitionResponse(result, reply.usage());
    }

    private CliReply call(RecognitionTask task) {
        acquirePermit();
        long startedAt = System.nanoTime();
        try (CallDirectory directory = CallDirectory.create(properties.workDir())) {
            CliInvocation invocation = cli.prepare(task, directory);
            CliOutputHandler handler = cli.newOutputHandler();
            ProcessOutcome outcome = runner.run(invocation, directory, handler::onLine,
                    properties.timeout(), properties.maxOutputBytes());
            CliReply reply = handler.complete(outcome);
            authState.markAccepted();
            log.info("Вызов {} через {}: {} мс", task.kind(), cli.provider(), elapsedMillis(startedAt));
            return reply;
        } catch (CliCallException exception) {
            if (exception.reason() == CliCallException.Reason.AUTH_REJECTED) {
                authState.markRejected();
            }
            log.warn("Вызов {} через {} не удался за {} мс: {} — {}", task.kind(), cli.provider(),
                    elapsedMillis(startedAt), exception.reason(), exception.getMessage());
            throw exception;
        } catch (IOException exception) {
            throw new CliCallException(CliCallException.Reason.START_FAILED,
                    "Не удалось подготовить каталог вызова", exception);
        } finally {
            permits.release();
        }
    }

    LunchAdvice parseLunch(String text) {
        return LunchContract.validate(parseJson(text, LunchAdvice.class));
    }

    RecognitionResult parseResult(String text) {
        return RecognitionContract.validate(parseJson(text, RecognitionResult.class));
    }

    private <T> T parseJson(String text, Class<T> type) {
        String json = RecognitionContract.stripCodeFence(text);
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            // CLI-агенты иногда добавляют к JSON пояснения — берём объект целиком от первой до последней скобки.
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return objectMapper.readValue(json.substring(start, end + 1), type);
                } catch (JsonProcessingException ignored) {
                    // ниже — общая ошибка
                }
            }
            throw new CliCallException(CliCallException.Reason.BAD_OUTPUT, "Модель вернула не JSON");
        }
    }

    private void acquirePermit() {
        try {
            if (!permits.tryAcquire(properties.queueTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AgentBusyException();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AgentBusyException();
        }
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
