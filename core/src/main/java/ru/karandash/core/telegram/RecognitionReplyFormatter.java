package ru.karandash.core.telegram;

import org.springframework.stereotype.Component;
import ru.karandash.contracts.ai.RecognitionItem;
import ru.karandash.contracts.ai.RecognitionResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Превращает результат распознавания в сообщение: позиции с диапазонами, итог и уточняющие вопросы.
 */
@Component
public class RecognitionReplyFormatter {

    static final int TELEGRAM_MESSAGE_LIMIT = 4096;
    private static final int MAX_NAME_LENGTH = 120;
    private static final int MAX_QUESTION_LENGTH = 300;

    public String format(RecognitionResult result) {
        StringBuilder text = new StringBuilder();
        if (!result.items().isEmpty()) {
            text.append("Похоже на:\n");
            long kcalMin = 0;
            long kcalMax = 0;
            for (RecognitionItem item : result.items()) {
                text.append('\n').append(itemLine(item));
                String macros = macrosLine(item);
                if (!macros.isEmpty()) {
                    text.append("\n   ").append(macros);
                }
                kcalMin += item.kcalMin();
                kcalMax += item.kcalMax();
            }
            if (result.items().size() > 1) {
                text.append("\n\nИтого: ").append(range(kcalMin, kcalMax)).append(" ккал");
            }
        }
        if (!result.questions().isEmpty()) {
            if (!text.isEmpty()) {
                text.append("\n\n");
            }
            text.append(result.items().isEmpty() ? "Чтобы оценить, уточните:" : "Чтобы оценить точнее, уточните:");
            for (int index = 0; index < result.questions().size(); index++) {
                text.append('\n').append(index + 1).append(". ")
                        .append(limit(result.questions().get(index).strip(), MAX_QUESTION_LENGTH));
            }
        }
        if (text.isEmpty()) {
            return TelegramTexts.NOTHING_RECOGNIZED;
        }
        if (!result.items().isEmpty()) {
            text.append("\n\n").append(TelegramTexts.ESTIMATE_NOTE);
        }
        return limit(text.toString(), TELEGRAM_MESSAGE_LIMIT);
    }

    private static String itemLine(RecognitionItem item) {
        List<String> parts = new ArrayList<>();
        String portion = range(item.portionGramsMin(), item.portionGramsMax());
        if (!portion.isEmpty()) {
            parts.add(portion + " г");
        }
        parts.add(range(item.kcalMin(), item.kcalMax()) + " ккал");
        return "• " + limit(item.name().strip(), MAX_NAME_LENGTH) + " — " + String.join(", ", parts);
    }

    private static String macrosLine(RecognitionItem item) {
        List<String> parts = new ArrayList<>();
        addMacro(parts, "Б", item.proteinGramsMin(), item.proteinGramsMax());
        addMacro(parts, "Ж", item.fatGramsMin(), item.fatGramsMax());
        addMacro(parts, "У", item.carbsGramsMin(), item.carbsGramsMax());
        parts.add("уверенность " + item.confidence().movePointRight(2).setScale(0, RoundingMode.HALF_UP) + "%");
        return String.join(" · ", parts);
    }

    private static void addMacro(List<String> parts, String label, BigDecimal min, BigDecimal max) {
        String value = range(min, max);
        if (!value.isEmpty()) {
            parts.add(label + " " + value + " г");
        }
    }

    private static String range(long min, long max) {
        return min == max ? Long.toString(min) : min + "–" + max;
    }

    private static String range(BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return "";
        }
        if (min == null || max == null || min.compareTo(max) == 0) {
            return number(min == null ? max : min);
        }
        return number(min) + "–" + number(max);
    }

    private static String number(BigDecimal value) {
        return value.setScale(Math.min(Math.max(value.scale(), 0), 1), RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    private static String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
    }
}
