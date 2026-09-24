package ru.karandash.core.streak;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Серия дней: сколько дней подряд человек записывает еду.
 * Пропуск до трёх дней подряд серию не рвёт — на четвёртый она начинается заново.
 */
@Service
public class StreakService {

    /** Сколько дней подряд можно пропустить, не потеряв серию. */
    public static final int FORGIVEN_DAYS = 3;

    private final StreakRepository streaks;

    public StreakService(StreakRepository streaks) {
        this.streaks = streaks;
    }

    /** Запись еды за день: продолжает серию, начинает новую или ничего не меняет, если день уже засчитан. */
    @Transactional
    public Streak record(UUID accountId, LocalDate day) {
        StreakEntity entity = streaks.findById(accountId).orElseGet(() -> new StreakEntity(accountId));
        LocalDate last = entity.getLastActiveOn();
        if (day.equals(last)) {
            return state(entity, day);
        }
        int days = continues(last, day) ? entity.getCurrentDays() + 1 : 1;
        entity.markActive(day, days);
        streaks.saveAndFlush(entity);
        return state(entity, day);
    }

    /** Состояние серии на сегодня. Серия, просроченная больше чем на три дня, показывается нулевой. */
    @Transactional(readOnly = true)
    public Streak today(UUID accountId, LocalDate today) {
        return streaks.findById(accountId)
                .map(entity -> state(entity, today))
                .orElseGet(() -> new Streak(0, 0, false, null, FORGIVEN_DAYS));
    }

    /** Убирает серии, которые уже не спасти: раз в день, чтобы цифра в сводке была честной. */
    @Transactional
    public void expire(UUID accountId, LocalDate today) {
        streaks.findById(accountId).ifPresent(entity -> {
            if (entity.getCurrentDays() > 0 && !continues(entity.getLastActiveOn(), today)) {
                entity.reset();
                streaks.saveAndFlush(entity);
            }
        });
    }

    private static Streak state(StreakEntity entity, LocalDate today) {
        LocalDate last = entity.getLastActiveOn();
        boolean alive = continues(last, today) || today.equals(last);
        int days = alive ? entity.getCurrentDays() : 0;
        long missed = last == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(last, today) - 1);
        int left = (int) Math.max(0, FORGIVEN_DAYS - missed);
        return new Streak(days, entity.getBestDays(), today.equals(last), last, alive ? left : FORGIVEN_DAYS);
    }

    /**
     * Серия продолжается, если между последним засчитанным днём и сегодня пропущено не больше трёх дней:
     * запись вчера — это ноль пропусков, запись четыре дня назад — три.
     */
    private static boolean continues(LocalDate last, LocalDate day) {
        if (last == null) {
            return false;
        }
        long gap = ChronoUnit.DAYS.between(last, day);
        return gap > 0 && gap <= FORGIVEN_DAYS + 1;
    }
}
