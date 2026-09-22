package ru.karandash.contracts.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Один вариант обеда: набор блюд из меню с оценкой калорий и цены.
 *
 * @param items названия блюд ровно так, как они называются в меню
 */
public record LunchOption(
        String title,
        List<String> items,
        @JsonProperty("kcal_min")
        Integer kcalMin,
        @JsonProperty("kcal_max")
        Integer kcalMax,
        Integer price,
        String why
) {

    public LunchOption {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
