package ru.karandash.core.canteen;

import org.junit.jupiter.api.Test;
import ru.karandash.contracts.ai.LunchAdvice;
import ru.karandash.contracts.ai.LunchOption;
import ru.karandash.contracts.ai.ModelUsage;
import ru.karandash.core.ai.ModelLunchAdvice;
import ru.karandash.core.ai.ModelUnavailableException;
import ru.karandash.core.ai.RecognitionModel;
import ru.karandash.core.calc.GoalDirection;
import ru.karandash.core.diary.DiaryDay;
import ru.karandash.core.profile.DailyTarget;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Подбор обеда: что уходит модели и что мы принимаем обратно.
 */
class LunchAdvisorTest {

    private static final CanteenMenu MENU = new CanteenMenu(List.of(
            new CanteenDish("Горячие блюда", "Гречка по купечески с курицей", new BigDecimal("120"),
                    new BigDecimal("250"), new BigDecimal("109.5"), new BigDecimal("8"),
                    new BigDecimal("2.5"), new BigDecimal("13.7"), "https://canteen/grechka.jpg"),
            new CanteenDish("Салаты", "Салат Оливье", new BigDecimal("60"), new BigDecimal("110"),
                    new BigDecimal("163.8"), new BigDecimal("6.3"), new BigDecimal("13.2"), new BigDecimal("4.9"),
                    null),
            new CanteenDish("Супы", "Солянка", new BigDecimal("140"), null, null, null, null, null, null)));

    private static final DiaryDay EATEN = new DiaryDay(LocalDate.of(2026, 9, 22), List.of(), 380, 480);
    private static final Optional<DailyTarget> TARGET =
            Optional.of(new DailyTarget(2216, 2449, GoalDirection.LOSS));

    @Test
    void promptCarriesMenuWithNutritionAndPersonalBudget() {
        AtomicReference<String> prompt = new AtomicReference<>();
        LunchAdvisor advisor = new LunchAdvisor(model(prompt, advice(
                new LunchOption("Сытный", List.of("Гречка по купечески с курицей", "Салат Оливье"),
                        270, 300, 999, "Много белка"))));

        Optional<LunchSuggestion> suggestion = advisor.advise(MENU, EATEN, TARGET);

        assertThat(prompt.get())
                .contains("Гречка по купечески с курицей, 250 г, 120 ₽, 109,5 ккал")
                .contains("Солянка, 140 ₽")
                .contains("цель — сбросить вес, дневная норма 2216–2449 ккал")
                .contains("Сегодня уже съедено 380–480 ккал, до нормы осталось 1736–2069 ккал");
        assertThat(suggestion).isPresent();
        LunchSuggestion.Option option = suggestion.get().options().getFirst();
        assertThat(option.title()).isEqualTo("Сытный");
        assertThat(option.dishes()).extracting(CanteenDish::name)
                .containsExactly("Гречка по купечески с курицей", "Салат Оливье");
        assertThat(option.price()).as("цену считаем по меню, а не со слов модели").isEqualByComparingTo("180");
    }

    @Test
    void dropsOptionsWithDishesThatAreNotOnTheMenu() {
        LunchAdvisor advisor = new LunchAdvisor(model(new AtomicReference<>(), advice(
                new LunchOption("Выдуманный", List.of("Стейк рибай"), 500, 700, 100, null),
                new LunchOption("Настоящий", List.of("Солянка"), 300, 400, 140, null))));

        Optional<LunchSuggestion> suggestion = advisor.advise(MENU, EATEN, TARGET);

        assertThat(suggestion).isPresent();
        assertThat(suggestion.get().options()).extracting(LunchSuggestion.Option::title)
                .as("купить можно только то, что есть в меню")
                .containsExactly("Настоящий");
    }

    @Test
    void staysQuietWhenNothingSurvivesOrModelIsDown() {
        LunchAdvisor hallucinating = new LunchAdvisor(model(new AtomicReference<>(), advice(
                new LunchOption("Выдуманный", List.of("Стейк рибай"), 500, 700, 100, null))));
        LunchAdvisor broken = new LunchAdvisor(new RecognitionModel() {
            @Override
            public ru.karandash.contracts.ai.RecognitionResult recognizeText(String description) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ru.karandash.contracts.ai.RecognitionResult recognizePhoto(byte[] image, String contentType) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<ModelLunchAdvice> adviseLunch(String prompt) {
                throw new ModelUnavailableException("агент недоступен");
            }
        });

        assertThat(hallucinating.advise(MENU, EATEN, TARGET)).isEmpty();
        assertThat(broken.advise(MENU, EATEN, TARGET)).isEmpty();
        assertThat(hallucinating.advise(new CanteenMenu(List.of()), EATEN, TARGET)).isEmpty();
    }

    @Test
    void withoutProfileAsksForOrdinaryLunch() {
        AtomicReference<String> prompt = new AtomicReference<>();
        LunchAdvisor advisor = new LunchAdvisor(model(prompt, advice(
                new LunchOption("Обычный", List.of("Солянка"), 300, 400, 140, null))));

        advisor.advise(MENU, EATEN, Optional.empty());

        assertThat(prompt.get())
                .contains("нормы калорий он пока не считал")
                .doesNotContain("дневная норма");
    }

    private static LunchAdvice advice(LunchOption... options) {
        return new LunchAdvice(List.of(options));
    }

    private static RecognitionModel model(AtomicReference<String> prompt, LunchAdvice advice) {
        return new RecognitionModel() {
            @Override
            public ru.karandash.contracts.ai.RecognitionResult recognizeText(String description) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ru.karandash.contracts.ai.RecognitionResult recognizePhoto(byte[] image, String contentType) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<ModelLunchAdvice> adviseLunch(String request) {
                prompt.set(request);
                return Optional.of(new ModelLunchAdvice(advice, ModelUsage.unknown()));
            }
        };
    }
}
