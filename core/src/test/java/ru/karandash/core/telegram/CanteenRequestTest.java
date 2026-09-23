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
    void understandsRequestsWithoutTheWordCanteen() {
        assertThat(TelegramIntakeService.asksAboutCanteen("что мне поесть из столовой на 300 калорий")).isTrue();
        assertThat(TelegramIntakeService.asksAboutCanteen("что поесть на 300 ккал")).isTrue();
        assertThat(TelegramIntakeService.asksAboutCanteen("чем пообедать сегодня?")).isTrue();
        assertThat(TelegramIntakeService.asksAboutCanteen("посоветуй что взять, хочу полегче")).isTrue();
    }

    @Test
    void knowsWhenSomeoneTellsAboutEatenFood() {
        assertThat(TelegramIntakeService.tellsAboutEatenFood("съел вариант 1")).isTrue();
        assertThat(TelegramIntakeService.tellsAboutEatenFood("пообедал в столовой супом")).isTrue();
        assertThat(TelegramIntakeService.tellsAboutEatenFood("запиши борщ и компот")).isTrue();
        assertThat(TelegramIntakeService.tellsAboutEatenFood("а полегче можно?")).isFalse();
        assertThat(TelegramIntakeService.asksAboutCanteen("съел суп в столовой и компот")).isFalse();
    }

    @Test
    void leavesFoodReportsToTheDiary() {
        assertThat(TelegramIntakeService.asksAboutCanteen("съел суп в столовой и компот")).isFalse();
        assertThat(TelegramIntakeService.asksAboutCanteen("гречка с курицей, обычная тарелка")).isFalse();
        assertThat(TelegramIntakeService.asksAboutCanteen("Тифтелька в сливочном соусе")).isFalse();
        assertThat(TelegramIntakeService.asksAboutCanteen("два яйца и сок")).isFalse();
    }
}
