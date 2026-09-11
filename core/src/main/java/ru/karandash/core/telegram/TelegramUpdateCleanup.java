package ru.karandash.core.telegram;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
class TelegramUpdateCleanup {

    private final TelegramUpdateRepository repository;
    private final TelegramIntakeProperties properties;

    TelegramUpdateCleanup(TelegramUpdateRepository repository, TelegramIntakeProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${karandash.telegram.update-cleanup-delay-ms:3600000}")
    void deleteExpired() {
        repository.deleteReceivedBefore(Instant.now().minus(properties.updateRetention()));
    }
}
