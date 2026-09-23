package ru.karandash.core.backup;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Метрики резервной копии. Главная — возраст последней удачной копии: если ночной запуск перестал
 * получаться, всё остальное продолжает работать как ни в чём не бывало, и заметить это можно только так.
 * Значения берутся из базы, поэтому переживают перезапуск ядра.
 */
@Component
public class BackupMetrics {

    private static final Logger log = LoggerFactory.getLogger(BackupMetrics.class);
    /** Копии ещё не было ни разу: возраст «очень большой», чтобы алерт не молчал у нового сервиса. */
    private static final double NEVER_SECONDS = Duration.ofDays(365).toSeconds();

    private final BackupService backups;
    private final Clock clock;

    public BackupMetrics(MeterRegistry registry, BackupService backups, Clock clock) {
        this.backups = backups;
        this.clock = clock;
        Gauge.builder("karandash.backup.age", this, BackupMetrics::lastSuccessAgeSeconds)
                .description("Сколько секунд прошло с последней удачной копии базы")
                .baseUnit("seconds")
                .register(registry);
        Gauge.builder("karandash.backup.size", this, BackupMetrics::sizeBytes)
                .description("Размер последней копии базы")
                .baseUnit("bytes")
                .register(registry);
        Gauge.builder("karandash.backup.duration", this, BackupMetrics::durationSeconds)
                .description("Сколько заняла последняя копия базы")
                .baseUnit("seconds")
                .register(registry);
        Gauge.builder("karandash.backup.ok", this, BackupMetrics::lastRunOk)
                .description("Последний запуск копии: 1 — удачно, 0 — нет")
                .register(registry);
    }

    double lastSuccessAgeSeconds() {
        return last()
                .map(BackupReportEntity::getLastSuccessAt)
                .map(at -> (double) Duration.between(at, clock.instant()).toSeconds())
                .orElse(NEVER_SECONDS);
    }

    double sizeBytes() {
        return last().map(BackupReportEntity::getSizeBytes).orElse(0L);
    }

    double durationSeconds() {
        return last().map(report -> report.getDurationMs() / 1000.0).orElse(0.0);
    }

    double lastRunOk() {
        return last().map(report -> report.isOk() ? 1.0 : 0.0).orElse(0.0);
    }

    /** База может быть недоступна ровно в момент снятия метрик — это не повод ронять весь эндпоинт. */
    private Optional<BackupReportEntity> last() {
        try {
            return backups.last(BackupService.POSTGRES);
        } catch (RuntimeException exception) {
            log.debug("Отчёт о копии базы недоступен: {}", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** Для тестов: время, от которого считается возраст. */
    Instant now() {
        return clock.instant();
    }
}
