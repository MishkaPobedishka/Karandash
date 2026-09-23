package ru.karandash.core.backup;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Метрики копий: по строке на сервис, возраст считается от последнего удачного запуска.
 */
class BackupMetricsTest {

    private static final Instant NOW = Instant.parse("2026-09-24T06:00:00Z");

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final BackupService backups = mock(BackupService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void reportsHugeAgeWhileThereWasNoSuccessfulBackup() {
        BackupReportEntity never = new BackupReportEntity("karandash");
        never.apply(new BackupReport("karandash", false, 0, 900, "pg_dump не отработал"), NOW);
        when(backups.all()).thenReturn(List.of(never));

        new BackupMetrics(registry, backups, clock).refresh();

        assertThat(gauge("backup.age", "karandash")).isGreaterThan(Duration.ofDays(300).toSeconds());
        assertThat(gauge("backup.ok", "karandash")).isZero();
    }

    @Test
    void countsAgeFromLastSuccessAndKeepsServicesApart() {
        BackupReportEntity karandash = new BackupReportEntity("karandash");
        karandash.apply(new BackupReport("karandash", true, 8_388_608, 21_000, null), NOW.minus(Duration.ofHours(6)));
        // Ночью копия не получилась: время последнего успеха от этого не сдвигается.
        karandash.apply(new BackupReport("karandash", false, 0, 900, "дамп не читается"),
                NOW.minus(Duration.ofMinutes(30)));
        BackupReportEntity chimney = new BackupReportEntity("chimney");
        chimney.apply(new BackupReport("chimney", true, 1_048_576, 4_000, null), NOW.minus(Duration.ofHours(2)));
        when(backups.all()).thenReturn(List.of(karandash, chimney));

        new BackupMetrics(registry, backups, clock).refresh();

        assertThat(gauge("backup.age", "karandash")).isEqualTo(Duration.ofHours(6).toSeconds());
        assertThat(gauge("backup.ok", "karandash")).isZero();
        assertThat(gauge("backup.size", "karandash")).isZero();
        assertThat(gauge("backup.age", "chimney")).isEqualTo(Duration.ofHours(2).toSeconds());
        assertThat(gauge("backup.size", "chimney")).isEqualTo(1_048_576);
        assertThat(gauge("backup.duration", "chimney")).isEqualTo(4.0);
        assertThat(gauge("backup.ok", "chimney")).isEqualTo(1.0);
    }

    @Test
    void takesServiceNameFromTheReportAndCleansIt() {
        assertThat(new BackupReport(null, true, 1, 1, null).service()).isEqualTo("karandash");
        assertThat(new BackupReport("Tracker DB", true, 1, 1, null).service()).isEqualTo("tracker-db");
    }

    private double gauge(String name, String service) {
        return registry.get(name).tag("service", service).gauge().value();
    }
}
