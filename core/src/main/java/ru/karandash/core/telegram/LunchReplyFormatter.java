package ru.karandash.core.telegram;

import org.springframework.stereotype.Component;
import ru.karandash.contracts.telegram.ReplyButton;
import ru.karandash.contracts.telegram.ReplyPhoto;
import ru.karandash.core.canteen.CanteenDish;
import ru.karandash.core.canteen.CanteenMenu;
import ru.karandash.core.canteen.LunchRemaining;
import ru.karandash.core.canteen.LunchSuggestion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static ru.karandash.core.telegram.RecognitionReplyFormatter.TELEGRAM_MESSAGE_LIMIT;
import static ru.karandash.core.telegram.RecognitionReplyFormatter.limit;
import static ru.karandash.core.telegram.RecognitionReplyFormatter.range;

/**
 * Показывает варианты обеда из столовой: что взять, сколько это калорий и денег, и само меню.
 */
@Component
public class LunchReplyFormatter {

    /** Кнопка «Меню столовой»: одна и та же в приветствии, подсказке и утренней рассылке. */
    public ReplyButton menuButton() {
        return new DialogCallback(DialogCallback.Action.LUNCH_SHOW).button(TelegramTexts.BUTTON_LUNCH_MENU);
    }

    /** Утренняя рассылка — с приветственной строкой. */
    public String mailing(LunchSuggestion suggestion, Optional<LunchRemaining> remaining) {
        return limit(TelegramTexts.LUNCH_MAILING_TITLE + "\n\n" + body(suggestion, remaining),
                TELEGRAM_MESSAGE_LIMIT);
    }

    /** Ответ на команду — без приветствия. */
    public String answer(LunchSuggestion suggestion, Optional<LunchRemaining> remaining) {
        return limit(TelegramTexts.LUNCH_ANSWER_TITLE + "\n\n" + body(suggestion, remaining), TELEGRAM_MESSAGE_LIMIT);
    }

    /**
     * Всё сегодняшнее меню по категориям. Возвращает несколько сообщений: в одно Telegram не влезает.
     */
    public List<String> menu(CanteenMenu menu) {
        List<String> messages = new ArrayList<>();
        StringBuilder text = new StringBuilder(TelegramTexts.LUNCH_MENU_TITLE);
        String category = null;
        for (CanteenDish dish : menu.dishes()) {
            StringBuilder piece = new StringBuilder();
            if (!dish.category().equals(category)) {
                category = dish.category();
                piece.append("\n\n").append(category).append(':');
            }
            piece.append("\n• ").append(dish.name());
            if (dish.price() != null) {
                piece.append(" — ").append(number(dish.price())).append(" ₽");
            }
            if (dish.weightGrams() != null) {
                piece.append(", ").append(number(dish.weightGrams())).append(" г");
            }
            if (dish.hasNutrition()) {
                piece.append(", ").append(number(dish.kcal())).append(" ккал");
            }
            if (text.length() + piece.length() > TELEGRAM_MESSAGE_LIMIT) {
                messages.add(text.toString());
                text = new StringBuilder(category + ":");
                // Первый кусок новой части уже содержит заголовок категории — не повторяем его.
                piece = new StringBuilder(piece.substring(piece.indexOf("\n• ")));
            }
            text.append(piece);
        }
        if (!text.isEmpty()) {
            messages.add(text.toString());
        }
        return messages;
    }

    /** Фотографии блюд из предложенных вариантов — по одной на блюдо, без повторов. */
    public List<ReplyPhoto> photos(LunchSuggestion suggestion) {
        Set<String> seen = new LinkedHashSet<>();
        List<ReplyPhoto> photos = new ArrayList<>();
        for (LunchSuggestion.Option option : suggestion.options()) {
            for (CanteenDish dish : option.dishes()) {
                if (!dish.hasPhoto() || !seen.add(dish.name())) {
                    continue;
                }
                if (photos.size() == ReplyPhoto.MAX_IN_ALBUM) {
                    return photos;
                }
                photos.add(new ReplyPhoto(dish.photoUrl(), limit(caption(dish), ReplyPhoto.MAX_CAPTION)));
            }
        }
        return photos;
    }

    private static String caption(CanteenDish dish) {
        StringBuilder text = new StringBuilder(dish.name());
        if (dish.price() != null) {
            text.append(" — ").append(number(dish.price())).append(" ₽");
        }
        if (dish.weightGrams() != null) {
            text.append(", ").append(number(dish.weightGrams())).append(" г");
        }
        if (dish.hasNutrition()) {
            text.append(", ").append(number(dish.kcal())).append(" ккал");
        }
        return text.toString();
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
