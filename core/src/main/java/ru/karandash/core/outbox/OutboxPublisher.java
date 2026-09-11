package ru.karandash.core.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int[] RETRY_SECONDS = {10, 60, 600};

    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPublisher(OutboxEventRepository repository, RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${karandash.outbox.poll-delay-ms:500}")
    @Transactional
    public void publishPending() {
        var events = repository.lockPending(Instant.now(), PageRequest.of(0, 50));
        for (OutboxEventEntity event : events) {
            try {
                rabbitTemplate.invoke(operations -> {
                    operations.convertAndSend(
                            RabbitTopologyConfiguration.EVENTS_EXCHANGE,
                            event.getEventType(),
                            event.getPayload()
                    );
                    operations.waitForConfirmsOrDie(5_000);
                    return true;
                });
                event.markPublished(Instant.now());
            } catch (RuntimeException exception) {
                int retryIndex = Math.min(event.getAttempts(), RETRY_SECONDS.length - 1);
                event.markFailed(exception.getMessage(),
                        Instant.now().plus(RETRY_SECONDS[retryIndex], ChronoUnit.SECONDS));
                log.warn("Не удалось опубликовать событие outbox {}", event.getId(), exception);
            }
        }
    }
}

