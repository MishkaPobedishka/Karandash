package ru.karandash.core.backup;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Метрики копии базы: пока копии нет — возраст заведомо огромный, чтобы алерт не молчал.
 */
class BackupMetricsTest {

    private static final Instant NOW = Instant.parse("2026-09-24T06:00:00Z");

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final BackupService backups = mock(BackupService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void reportsHugeAgeWhileThereWasNoBackup() {
        when(backups.last(anyString())).thenReturn(Optional.empty());
        BackupMetrics metrics = new BackupMetrics(registry, backups, clock);

        assertThat(metrics.lastSuccessAgeSeconds()).isGreaterThan(Duration.ofDays(300).toSeconds());
        assertThat(metrics.lastRunOk()).isZero();
        assertThat(registry.get("karandash.backup.age").gauge().value())
                .isEqualTo(metrics.lastSuccessAgeSeconds());
    }

    @Test
    void countsAgeFromLastSuccessNotFromLastRun() {
        BackupReportEntity report = new BackupReportEntity(BackupService.POSTGRES);
        report.apply(new BackupReport(true, 5_000_000, 12_000, null), NOW.minus(Duration.ofHours(6)));
        // Ночью копия не получилась: время последнего успеха от этого не сдвигается.
        report.apply(new BackupReport(false, 0, 900, "pg_dump не отработал"), NOW.minus(Duration.ofMinutes(30)));
        when(backups.last(anyString())).thenReturn(Optional.of(report));
        BackupMetrics metrics = new BackupMetrics(registry, backups, clock);

        assertThat(metrics.lastSuccessAgeSeconds()).isEqualTo(Duration.ofHours(6).toSeconds());
        assertThat(metrics.lastRunOk()).isZero();
        assertThat(metrics.sizeBytes()).isZero();
        assertThat(report.getMessage()).isEqualTo("pg_dump не отработал");
    }

    @Test
    void keepsSizeAndDurationOfASuccessfulBackup() {
        BackupReportEntity report = new BackupReportEntity(BackupService.POSTGRES);
        report.apply(new BackupReport(true, 8_388_608, 21_000, null), NOW.minus(Duration.ofHours(2)));
        when(backups.last(anyString())).thenReturn(Optional.of(report));
        BackupMetrics metrics = new BackupMetrics(registry, backups, clock);

        assertThat(metrics.sizeBytes()).isEqualTo(8_388_608);
        assertThat(metrics.durationSeconds()).isEqualTo(21.0);
        assertThat(metrics.lastRunOk()).isEqualTo(1.0);
        assertThat(metrics.lastSuccessAgeSeconds()).isEqualTo(Duration.ofHours(2).toSeconds());
    }
}
