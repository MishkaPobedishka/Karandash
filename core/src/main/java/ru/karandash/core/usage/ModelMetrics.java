package ru.karandash.core.usage;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Метрики вызовов модели. Главная — сколько времени прошло с последнего удачного вызова:
 * когда у CLI протухают учётные данные, сервис остаётся живым и просто молча отвечает отказами,
 * и заметить это можно только по возрасту последнего успеха.
 */
@Component
public class ModelMetrics {

    private static final Logger log = LoggerFactory.getLogger(ModelMetrics.class);
    private static final String FAILURE_SUFFIX = "_FAILED";

    private final MeterRegistry registry;
    private final UsageRecordRepository repository;
    private final Clock clock;
    private final AtomicReference<Instant> lastSuccess = new AtomicReference<>();

    public ModelMetrics(MeterRegistry registry, UsageRecordRepository repository, Clock clock) {
        this.registry = registry;
        this.repository = repository;
        this.clock = clock;
        this.lastSuccess.set(clock.instant());
        Gauge.builder("karandash.model.last.success.age", this, ModelMetrics::lastSuccessAgeSeconds)
                .description("Сколько секунд прошло с последнего удачного вызова модели")
                .baseUnit("seconds")
                .register(registry);
    }

    /** При старте берём последний удачный вызов из базы: иначе перезапуск выглядел бы как «всё хорошо». */
    @EventListener(ApplicationReadyEvent.class)
    public void restoreLastSuccess() {
        try {
            repository.findLastSuccessAt(FAILURE_SUFFIX).ifPresent(lastSuccess::set);
        } catch (RuntimeException exception) {
            log.warn("Не удалось узнать время последнего удачного вызова модели: {}",
                    exception.getClass().getSimpleName());
        }
    }

    /** Вид вызова заканчивается на {@code _FAILED} — значит, модель не ответила. */
    void record(String kind) {
        boolean success = !kind.endsWith(FAILURE_SUFFIX);
        Counter.builder("karandash.model.calls")
                .description("Вызовы модели по видам и исходу")
                .tag("kind", success ? kind : kind.substring(0, kind.length() - FAILURE_SUFFIX.length()))
                .tag("outcome", success ? "success" : "failure")
                .register(registry)
                .increment();
        if (success) {
            lastSuccess.set(clock.instant());
        }
    }

    double lastSuccessAgeSeconds() {
        return Duration.between(lastSuccess.get(), clock.instant()).toSeconds();
    }
}
