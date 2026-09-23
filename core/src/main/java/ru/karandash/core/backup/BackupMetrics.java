package ru.karandash.core.backup;

import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Метрики резервных копий — по одной строке на сервис, который о себе отчитывается.
 * Главная — возраст последней удачной копии: если ночной запуск перестал получаться, всё остальное
 * работает как ни в чём не бывало, и заметить это можно только так.
 * Значения читаются из базы, поэтому переживают перезапуск ядра.
 */
@Component
public class BackupMetrics {

    private static final Logger log = LoggerFactory.getLogger(BackupMetrics.class);
    /** Копии ещё не было ни разу: возраст «очень большой», чтобы алерт не молчал у нового сервиса. */
    static final double NEVER_SECONDS = Duration.ofDays(365).toSeconds();

    private final BackupService backups;
    private final Clock clock;
    private final MultiGauge age;
    private final MultiGauge size;
    private final MultiGauge duration;
    private final MultiGauge ok;

    public BackupMetrics(MeterRegistry registry, BackupService backups, Clock clock) {
        this.backups = backups;
        this.clock = clock;
        this.age = MultiGauge.builder("backup.age")
                .description("Сколько секунд прошло с последней удачной копии")
                .baseUnit("seconds")
                .register(registry);
        this.size = MultiGauge.builder("backup.size")
                .description("Размер последней копии")
                .baseUnit("bytes")
                .register(registry);
        this.duration = MultiGauge.builder("backup.duration")
                .description("Сколько заняла последняя копия")
                .baseUnit("seconds")
                .register(registry);
        this.ok = MultiGauge.builder("backup.ok")
                .description("Последний запуск копии: 1 — удачно, 0 — нет")
                .register(registry);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refreshOnStartup() {
        refresh();
    }

    /** Возраст копии должен расти между запусками, поэтому пересчитываем его регулярно. */
    @Scheduled(fixedDelayString = "${karandash.backup.refresh-interval:60000}")
    public void refresh() {
        List<BackupReportEntity> reports;
        try {
            reports = backups.all();
        } catch (RuntimeException exception) {
            // База может быть недоступна ровно в этот момент — метрики подождут следующего круга.
            log.debug("Отчёты о копиях недоступны: {}", exception.getClass().getSimpleName());
            return;
        }
        age.register(reports.stream().map(report -> row(report, ageSeconds(report))).toList(), true);
        size.register(reports.stream().map(report -> row(report, (double) report.getSizeBytes())).toList(), true);
        duration.register(reports.stream()
                .map(report -> row(report, report.getDurationMs() / 1000.0)).toList(), true);
        ok.register(reports.stream().map(report -> row(report, report.isOk() ? 1.0 : 0.0)).toList(), true);
    }

    private static MultiGauge.Row<Number> row(BackupReportEntity report, double value) {
        return MultiGauge.Row.of(Tags.of("service", report.getName()), value);
    }

    double ageSeconds(BackupReportEntity report) {
        return report.getLastSuccessAt() == null
                ? NEVER_SECONDS
                : Duration.between(report.getLastSuccessAt(), clock.instant()).toSeconds();
    }
}
