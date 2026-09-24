package ru.karandash.core.streak;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Правило серии: пропуск до трёх дней подряд прощается, четвёртый начинает счёт заново.
 */
class StreakRuleTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 24);
    private static final UUID ACCOUNT = UUID.randomUUID();

    private final Map<UUID, StreakEntity> store = new HashMap<>();
    private final StreakService streaks = new StreakService(repository());

    @Test
    void countsDaysInARow() {
        streaks.record(ACCOUNT, DAY.minusDays(2));
        streaks.record(ACCOUNT, DAY.minusDays(1));
        Streak streak = streaks.record(ACCOUNT, DAY);

        assertThat(streak.days()).isEqualTo(3);
        assertThat(streak.activeToday()).isTrue();
        assertThat(streak.atRisk()).isFalse();
    }

    @Test
    void countsOneDayOnce() {
        streaks.record(ACCOUNT, DAY);
        Streak streak = streaks.record(ACCOUNT, DAY);

        assertThat(streak.days()).isOne();
    }

    @Test
    void forgivesUpToThreeMissedDays() {
        streaks.record(ACCOUNT, DAY.minusDays(4));

        // Пропущены три дня подряд — на четвёртый запись продолжает серию.
        assertThat(streaks.record(ACCOUNT, DAY).days()).isEqualTo(2);
    }

    @Test
    void startsOverAfterFourMissedDays() {
        streaks.record(ACCOUNT, DAY.minusDays(9));
        streaks.record(ACCOUNT, DAY.minusDays(5));

        assertThat(streaks.record(ACCOUNT, DAY).days()).isOne();
    }

    @Test
    void keepsBestResultAndShowsHowManyDaysAreLeft() {
        for (int day = 12; day >= 1; day--) {
            streaks.record(ACCOUNT, DAY.minusDays(day));
        }

        Streak today = streaks.today(ACCOUNT, DAY);
        assertThat(today.days()).isEqualTo(12);
        assertThat(today.best()).isEqualTo(12);
        assertThat(today.activeToday()).as("сегодня ещё ничего не записано").isFalse();
        assertThat(today.atRisk()).isTrue();
        assertThat(today.daysLeft()).as("вчера записал — впереди все три дня прощения").isEqualTo(3);

        assertThat(streaks.today(ACCOUNT, DAY.plusDays(2)).daysLeft()).as("остался один день").isOne();
        assertThat(streaks.today(ACCOUNT, DAY.plusDays(3)).daysLeft()).as("последний день").isZero();
        assertThat(streaks.today(ACCOUNT, DAY.plusDays(4)).days()).as("серия сгорела").isZero();
    }

    @Test
    void expireClearsTheStreakOnlyWhenItIsGone() {
        streaks.record(ACCOUNT, DAY);

        streaks.expire(ACCOUNT, DAY.plusDays(3));
        assertThat(store.get(ACCOUNT).getCurrentDays()).as("ещё можно спасти").isOne();

        streaks.expire(ACCOUNT, DAY.plusDays(5));
        assertThat(store.get(ACCOUNT).getCurrentDays()).isZero();
        assertThat(store.get(ACCOUNT).getBestDays()).as("лучший результат остаётся").isOne();
    }

    private StreakRepository repository() {
        StreakRepository repository = mock(StreakRepository.class);
        when(repository.findById(any())).thenAnswer(call -> Optional.ofNullable(store.get(call.getArgument(0))));
        when(repository.saveAndFlush(any())).thenAnswer(call -> {
            StreakEntity entity = call.getArgument(0);
            store.put(ACCOUNT, entity);
            return entity;
        });
        return repository;
    }
}
