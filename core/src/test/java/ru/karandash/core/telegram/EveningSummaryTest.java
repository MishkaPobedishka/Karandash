package ru.karandash.core.telegram;

import org.junit.jupiter.api.Test;
import ru.karandash.core.streak.Streak;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Строка про серию: она же предупреждение, что день вот-вот пропадёт.
 */
class EveningSummaryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 24);

    @Test
    void tellsWhenThereIsNoStreakYet() {
        assertThat(EveningSummary.streakLine(new Streak(0, 3, false, null, 3)))
                .startsWith("Серии пока нет");
    }

    @Test
    void praisesWhenTheDayIsAlreadyCounted() {
        assertThat(EveningSummary.streakLine(new Streak(1, 1, true, DAY, 3)))
                .isEqualTo("🔥 Серия: 1 день подряд. Так держать.");
        assertThat(EveningSummary.streakLine(new Streak(22, 30, true, DAY, 3)))
                .isEqualTo("🔥 Серия: 22 дня подряд. Так держать.");
    }

    @Test
    void warnsWhileTheStreakCanStillBeSaved() {
        assertThat(EveningSummary.streakLine(new Streak(22, 30, false, DAY.minusDays(1), 2)))
                .contains("Серия: 22 дня")
                .contains("сегодня ещё ничего не записано")
                .contains("Пропустить можно ещё 2 дня");
    }

    @Test
    void saysPlainlyWhenItIsTheLastDay() {
        assertThat(EveningSummary.streakLine(new Streak(5, 9, false, DAY.minusDays(4), 0)))
                .contains("сегодня последний день");
    }

    @Test
    void writesDaysInRussian() {
        assertThat(EveningSummary.days(1)).isEqualTo("день");
        assertThat(EveningSummary.days(2)).isEqualTo("дня");
        assertThat(EveningSummary.days(5)).isEqualTo("дней");
        assertThat(EveningSummary.days(11)).isEqualTo("дней");
        assertThat(EveningSummary.days(21)).isEqualTo("день");
        assertThat(EveningSummary.days(112)).isEqualTo("дней");
    }
}
