package ru.karandash.core.diary;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Оценки, на которые пользователь так и не ответил, не должны лежать вечно.
 */
@Component
class MealDraftCleanup {

    private final MealDraftRepository drafts;
    private final DiaryProperties properties;
    private final Clock clock;

    MealDraftCleanup(MealDraftRepository drafts, DiaryProperties properties, Clock clock) {
        this.drafts = drafts;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${karandash.diary.draft-cleanup-delay-ms:3600000}")
    void deleteExpired() {
        drafts.deleteUpdatedBefore(clock.instant().minus(properties.draftRetention()));
    }
}
