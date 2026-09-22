package ru.karandash.core.usage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.karandash.contracts.ai.ModelUsage;

import java.math.RoundingMode;
import java.time.Duration;
import java.util.UUID;

/**
 * Учёт вызовов модели в {@code usage_record}. Сбой учёта не ломает ответ пользователю — только пишется в лог.
 */
@Service
public class UsageRecorder {

    private static final Logger log = LoggerFactory.getLogger(UsageRecorder.class);
    private static final String UNKNOWN = "unknown";

    private final UsageRecordRepository repository;
    private final ModelMetrics metrics;

    public UsageRecorder(UsageRecordRepository repository, ModelMetrics metrics) {
        this.repository = repository;
        this.metrics = metrics;
    }

    public void record(UUID accountId, String kind, String fallbackProvider, ModelUsage usage, Duration latency) {
        ModelUsage safeUsage = usage == null ? ModelUsage.unknown() : usage;
        metrics.record(kind);
        try {
            repository.save(new UsageRecordEntity(
                    accountId,
                    limit(kind, 32),
                    limit(firstNonBlank(safeUsage.provider(), fallbackProvider), 64),
                    limit(firstNonBlank(safeUsage.model(), UNKNOWN), 128),
                    safeUsage.inputTokens(),
                    safeUsage.outputTokens(),
                    safeUsage.costUsd() == null ? null : safeUsage.costUsd().setScale(6, RoundingMode.HALF_UP),
                    latency.toMillis()
            ));
        } catch (RuntimeException exception) {
            log.warn("Не удалось записать учёт вызова модели ({})", kind, exception);
        }
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? (fallback == null || fallback.isBlank() ? UNKNOWN : fallback) : value;
    }

    private static String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
