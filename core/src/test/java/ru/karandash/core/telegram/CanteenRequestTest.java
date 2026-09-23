package ru.karandash.core.telegram;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Где проходит граница между «подбери обед» и «я это съел»: первое идёт в столовую, второе в дневник.
 */
class CanteenRequestTest {

    @Test
    void recognizesRequestsAboutTheCanteen() {
        assertThat(TelegramIntakeService.asksAboutCanteen(
                "Че похавать в столовой на 600-700 ккал, но чтобы было первое и салатес")).isTrue();
        assertThat(TelegramIntakeService.asksAboutCanteen("Сам посмотри меню столовой брооо")).isTrue();
        assertThat(TelegramIntakeService.asksAboutCanteen("не видит меню столовой")).isTrue();
        assertThat(TelegramIntakeService.asksAboutCanteen("что взять в столовой?")).isTrue();
        assertThat(TelegramIntakeService.asksAboutCanteen("посоветуй обед в столовой")).isTrue();
    }

    @Test
    void leavesFoodReportsToTheDiary() {
        assertThat(TelegramIntakeService.asksAboutCanteen("съел суп в столовой и компот")).isFalse();
        assertThat(TelegramIntakeService.asksAboutCanteen("гречка с курицей, обычная тарелка")).isFalse();
        assertThat(TelegramIntakeService.asksAboutCanteen("Тифтелька в сливочном соусе")).isFalse();
        assertThat(TelegramIntakeService.asksAboutCanteen("два яйца и сок")).isFalse();
    }
}
