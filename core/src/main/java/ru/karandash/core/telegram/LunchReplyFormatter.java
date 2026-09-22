package ru.karandash.core.telegram;

import org.springframework.stereotype.Component;
import ru.karandash.core.canteen.CanteenDish;
import ru.karandash.core.canteen.LunchRemaining;
import ru.karandash.core.canteen.LunchSuggestion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

import static ru.karandash.core.telegram.RecognitionReplyFormatter.TELEGRAM_MESSAGE_LIMIT;
import static ru.karandash.core.telegram.RecognitionReplyFormatter.limit;
import static ru.karandash.core.telegram.RecognitionReplyFormatter.range;

/**
 * Показывает варианты обеда из столовой: что взять, сколько это калорий и денег.
 */
@Component
public class LunchReplyFormatter {

    /** Утренняя рассылка — с приветственной строкой. */
    public String mailing(LunchSuggestion suggestion, Optional<LunchRemaining> remaining) {
        return limit(TelegramTexts.LUNCH_MAILING_TITLE + "\n\n" + body(suggestion, remaining),
                TELEGRAM_MESSAGE_LIMIT);
    }

    /** Ответ на команду — без приветствия. */
    public String answer(LunchSuggestion suggestion, Optional<LunchRemaining> remaining) {
        return limit(TelegramTexts.LUNCH_ANSWER_TITLE + "\n\n" + body(suggestion, remaining), TELEGRAM_MESSAGE_LIMIT);
    }

    private static String body(LunchSuggestion suggestion, Optional<LunchRemaining> remaining) {
        StringBuilder text = new StringBuilder();
        int number = 1;
        for (LunchSuggestion.Option option : suggestion.options()) {
            text.append(number++).append(". ").append(option.title())
                    .append(" — ").append(range(option.kcalMin(), option.kcalMax())).append(" ккал");
            if (option.price() != null && option.price().signum() > 0) {
                text.append(", ").append(number(option.price())).append(" ₽");
            }
            text.append('\n');
            for (CanteenDish dish : option.dishes()) {
                text.append("   • ").append(dish.name());
                if (dish.weightGrams() != null) {
                    text.append(", ").append(number(dish.weightGrams())).append(" г");
                }
                text.append('\n');
            }
            if (option.why() != null && !option.why().isBlank()) {
                text.append("   ").append(option.why().strip()).append('\n');
            }
            text.append('\n');
        }
        remaining.ifPresent(left -> text.append("До дневной нормы осталось ")
                .append(range(left.kcalMin(), left.kcalMax())).append(" ккал.\n"));
        text.append(TelegramTexts.LUNCH_FOOTER);
        return text.toString();
    }

    private static String number(BigDecimal value) {
        return value.setScale(Math.min(Math.max(value.scale(), 0), 1), RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
