package ru.karandash.core.canteen;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Разговор о столовой. Пока он открыт, короткие реплики человека — «а полегче», «без мяса»,
 * «тогда на 300 ккал» — это уточнение подбора, а не новая еда в дневник.
 * Разговор закрывается сам через полчаса молчания и сразу, как только человек присылает еду.
 */
@Service
public class LunchDialogService {

    /** Сколько разговор про столовую живёт без новых реплик. */
    private static final Duration ALIVE = Duration.ofMinutes(30);
    /** Пожелания копятся, но не бесконечно: в запрос модели уходит только последнее осмысленное. */
    private static final int MAX_WISH_LENGTH = 600;

    private final LunchDialogRepository dialogs;
    private final Clock clock;

    public LunchDialogService(LunchDialogRepository dialogs, Clock clock) {
        this.dialogs = dialogs;
        this.clock = clock;
    }

    /** Открытый разговор, если он ещё живой. */
    @Transactional(readOnly = true)
    public Optional<LunchDialogEntity> active(UUID accountId) {
        return dialogs.findById(accountId)
                .filter(dialog -> Duration.between(dialog.getUpdatedAt(), clock.instant()).compareTo(ALIVE) < 0);
    }

    /** Начинает разговор или дописывает к нему новое пожелание. */
    @Transactional
    public String remember(UUID accountId, String wish) {
        String addition = wish == null ? "" : wish.strip();
        LunchDialogEntity dialog = dialogs.findById(accountId).orElseGet(() -> new LunchDialogEntity(accountId, ""));
        boolean expired = Duration.between(dialog.getUpdatedAt(), clock.instant()).compareTo(ALIVE) >= 0;
        String previous = expired ? "" : dialog.getWish();
        String combined = previous.isBlank() || addition.isBlank()
                ? (addition.isBlank() ? previous : addition)
                : previous + "\n" + addition;
        if (combined.length() > MAX_WISH_LENGTH) {
            // Держим хвост разговора: последние реплики важнее первых.
            combined = combined.substring(combined.length() - MAX_WISH_LENGTH);
        }
        dialog.setWish(combined);
        dialogs.saveAndFlush(dialog);
        return combined;
    }

    @Transactional
    public void close(UUID accountId) {
        dialogs.deleteById(accountId);
    }
}
