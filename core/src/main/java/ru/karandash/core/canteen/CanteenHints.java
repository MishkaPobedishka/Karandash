package ru.karandash.core.canteen;

import org.springframework.stereotype.Service;
import ru.karandash.contracts.ai.RecognitionContract;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Справка о сегодняшнем меню столовой для распознавания еды. По будням обед чаще всего оттуда,
 * и вес с пищевой ценностью лучше взять из меню, чем оценивать на глаз.
 */
@Service
public class CanteenHints {

    private final CanteenClient canteen;
    private final CanteenProperties properties;

    public CanteenHints(CanteenClient canteen, CanteenProperties properties) {
        this.canteen = canteen;
        this.properties = properties;
    }

    /** Меню на сегодня одной справкой или {@code null}, если столовая закрыта либо меню недоступно. */
    public String todayMenu() {
        if (!properties.workday()) {
            return null;
        }
        return canteen.today().map(menu -> RecognitionContract.canteenContext(lines(menu))).orElse(null);
    }

    static List<String> lines(CanteenMenu menu) {
        List<String> lines = new ArrayList<>();
        for (CanteenDish dish : menu.dishes()) {
            StringBuilder line = new StringBuilder(dish.name());
            if (dish.weightGrams() != null) {
                line.append(", ").append(number(dish.weightGrams())).append(" г");
            }
            if (dish.hasPortionNutrition()) {
                line.append(", ").append(number(dish.kcalPerPortion())).append(" ккал в порции")
                        .append(" (Б ").append(number(dish.proteinsPerPortion()))
                        .append(" Ж ").append(number(dish.fatsPerPortion()))
                        .append(" У ").append(number(dish.carbohydratesPerPortion())).append(")");
            } else if (dish.hasNutrition()) {
                line.append(", ").append(number(dish.kcal())).append(" ккал на 100 г");
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private static String number(BigDecimal value) {
        return value == null
                ? "?"
                : value.setScale(Math.min(Math.max(value.scale(), 0), 1), RoundingMode.HALF_UP)
                        .stripTrailingZeros().toPlainString();
    }
}
