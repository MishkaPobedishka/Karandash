package ru.karandash.core.usage;

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
 * Метрика, по которой видно молчащую модель: сервис жив, а вызовы не проходят.
 */
class ModelMetricsTest {

    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final UsageRecordRepository repository = mock(UsageRecordRepository.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void ageGrowsWhileOnlyFailuresCome() {
        ModelMetrics metrics = new ModelMetrics(registry, repository, clock);
        when(repository.findLastSuccessAt(anyString()))
                .thenReturn(Optional.of(NOW.minus(Duration.ofHours(5))));
        metrics.restoreLastSuccess();

        assertThat(metrics.lastSuccessAgeSeconds())
                .as("после перезапуска берём время последнего успеха из базы")
                .isEqualTo(Duration.ofHours(5).toSeconds());

        metrics.record("RECOGNITION_TEXT_FAILED");

        assertThat(metrics.lastSuccessAgeSeconds()).as("неудачный вызов возраст не сбрасывает")
                .isEqualTo(Duration.ofHours(5).toSeconds());
        assertThat(registry.get("karandash.model.calls")
                .tag("kind", "RECOGNITION_TEXT").tag("outcome", "failure").counter().count()).isOne();

        metrics.record("RECOGNITION_TEXT");

        assertThat(metrics.lastSuccessAgeSeconds()).isZero();
        assertThat(registry.get("karandash.model.calls")
                .tag("kind", "RECOGNITION_TEXT").tag("outcome", "success").counter().count()).isOne();
        assertThat(registry.get("karandash.model.last.success.age").gauge().value()).isZero();
    }

    @Test
    void startsFromLaunchWhenNothingIsRecordedYet() {
        when(repository.findLastSuccessAt(anyString())).thenReturn(Optional.empty());
        ModelMetrics metrics = new ModelMetrics(registry, repository, clock);

        metrics.restoreLastSuccess();

        assertThat(metrics.lastSuccessAgeSeconds())
                .as("пустая база не должна выглядеть как многолетнее молчание").isZero();
    }
}
