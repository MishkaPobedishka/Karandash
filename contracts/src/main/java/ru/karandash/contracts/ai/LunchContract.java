package ru.karandash.contracts.ai;

/**
 * Контракт подбора обеда в столовой: что модель обязана вернуть и как это проверяется.
 * Один и тот же для ядра и агент-адаптера.
 */
public final class LunchContract {

    public static final int MAX_OPTIONS = 3;
    public static final int MAX_DISHES_IN_OPTION = 4;
    private static final int MAX_TEXT_LENGTH = 200;

    public static final String MODEL_INSTRUCTIONS = """
            Ты подбираешь обед в заводской столовой по её сегодняшнему меню.
            Тебе дают меню (блюдо, вес и цена, иногда пищевая ценность) и сколько калорий у человека
            осталось на сегодня с учётом его цели.
            Верни только JSON вида
            {"options":[{"title":"...","items":["...","..."],"kcal_min":0,"kcal_max":0,"price":0,"why":"..."}]}
            От двух до трёх вариантов обеда, в каждом от одного до четырёх блюд.
            Бери блюда только из меню и пиши их названия слово в слово — выдуманное блюдо купить нельзя.
            Где пищевая ценность известна, считай по ней; где нет — оцени по названию и весу без ложной точности.
            Вариант должен укладываться в остаток калорий; если остаток мал, предложи самые лёгкие блюда
            и честно скажи об этом в why.
            title — короткое название варианта, why — одна фраза, почему он подходит.
            """;

    private LunchContract() {
    }

    public static LunchAdvice validate(LunchAdvice advice) {
        if (advice == null || advice.options() == null || advice.options().isEmpty()
                || advice.options().size() > MAX_OPTIONS) {
            throw new RecognitionContractException("Подбор обеда не соответствует контракту");
        }
        advice.options().forEach(LunchContract::validateOption);
        return advice;
    }

    private static void validateOption(LunchOption option) {
        if (option == null || blank(option.title()) || option.title().length() > MAX_TEXT_LENGTH
                || option.items() == null || option.items().isEmpty()
                || option.items().size() > MAX_DISHES_IN_OPTION
                || option.items().stream().anyMatch(LunchContract::blank)
                || option.kcalMin() == null || option.kcalMax() == null
                || option.kcalMin() < 0 || option.kcalMax() < option.kcalMin()
                || (option.why() != null && option.why().length() > MAX_TEXT_LENGTH)) {
            throw new RecognitionContractException("Вариант обеда не соответствует контракту");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
