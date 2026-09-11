package ru.karandash.core.telegram;

import org.junit.jupiter.api.Test;
import ru.karandash.contracts.ai.RecognitionItem;
import ru.karandash.contracts.ai.RecognitionResult;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecognitionReplyFormatterTest {

    private final RecognitionReplyFormatter formatter = new RecognitionReplyFormatter();

    @Test
    void formatsItemsWithRangesAndTotal() {
        String text = formatter.format(new RecognitionResult(List.of(
                item("Гречка с курицей", "250", "320", 380, 480, "25", "32", "0.7"),
                item("Хлеб", "30", "30", 70, 80, null, null, "0.9")
        ), List.of()));

        assertThat(text).contains("Похоже на:")
                .contains("• Гречка с курицей — 250–320 г, 380–480 ккал")
                .contains("Б 25–32 г · уверенность 70%")
                .contains("• Хлеб — 30 г, 70–80 ккал")
                .contains("Итого: 450–560 ккал")
                .contains("пока не записывается");
    }

    @Test
    void asksQuestionsWhenNothingRecognized() {
        String text = formatter.format(new RecognitionResult(List.of(), List.of("Что в тарелке?", "Какая порция?")));

        assertThat(text).isEqualTo("Чтобы оценить, уточните:\n1. Что в тарелке?\n2. Какая порция?");
    }

    @Test
    void handlesEmptyResultAndLongNames() {
        assertThat(formatter.format(new RecognitionResult(List.of(), List.of()))).contains("Не получилось узнать еду");

        String text = formatter.format(new RecognitionResult(
                List.of(item("а".repeat(5000), null, null, 100, 100, null, null, "0.5")), List.of()));
        assertThat(text.length()).isLessThanOrEqualTo(RecognitionReplyFormatter.TELEGRAM_MESSAGE_LIMIT);
        assertThat(text).contains("…").contains("100 ккал");
    }

    private static RecognitionItem item(String name, String portionMin, String portionMax, int kcalMin, int kcalMax,
                                        String proteinMin, String proteinMax, String confidence) {
        return new RecognitionItem(name, decimal(portionMin), decimal(portionMax), kcalMin, kcalMax,
                decimal(proteinMin), decimal(proteinMax), null, null, null, null, new BigDecimal(confidence));
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
