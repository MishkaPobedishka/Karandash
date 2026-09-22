package ru.karandash.contracts.ai;

import java.util.List;

/**
 * Что модель предлагает съесть сегодня в столовой.
 */
public record LunchAdvice(List<LunchOption> options) {

    public LunchAdvice {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
