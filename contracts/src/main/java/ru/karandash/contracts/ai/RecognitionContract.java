package ru.karandash.contracts.ai;

import java.math.BigDecimal;

/**
 * Контракт распознавания еды: что модель обязана вернуть и как это проверяется.
 * Один и тот же для любого провайдера модели — HTTP API и CLI-агента.
 */
public final class RecognitionContract {

    public static final int MAX_QUESTIONS = 2;

    public static final String MODEL_INSTRUCTIONS = """
            Ты оцениваешь состав обычной еды для личного дневника питания.
            Верни только JSON с массивами items и questions. Для каждой позиции укажи:
            name, portion_g_min, portion_g_max, kcal_min, kcal_max,
            protein_g_min, protein_g_max, fat_g_min, fat_g_max,
            carbs_g_min, carbs_g_max, confidence от 0 до 1.
            Не создавай ложную точность. Если оценка невозможна, не выдумывай числа,
            оставь items пустым и задай не более двух коротких уточняющих вопросов.
            """;

    public static final String PHOTO_REQUEST = "Распознай блюда, ингредиенты и диапазоны порций на фотографии.";

    private RecognitionContract() {
    }

    public static RecognitionResult validate(RecognitionResult result) {
        if (result == null || result.items() == null || result.questions() == null
                || result.questions().size() > MAX_QUESTIONS) {
            throw new RecognitionContractException("Результат распознавания не соответствует контракту");
        }
        result.items().forEach(item -> {
            if (item == null || item.name() == null || item.name().isBlank()
                    || item.kcalMin() == null || item.kcalMax() == null
                    || item.kcalMin() < 0 || item.kcalMax() < item.kcalMin()
                    || item.confidence() == null
                    || item.confidence().signum() < 0
                    || item.confidence().compareTo(BigDecimal.ONE) > 0) {
                throw new RecognitionContractException("Позиция распознавания не соответствует контракту");
            }
        });
        return result;
    }

    /**
     * Модели иногда оборачивают JSON в блок кода — снимаем обёртку перед разбором.
     */
    public static String stripCodeFence(String value) {
        String trimmed = value.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        return firstLineEnd >= 0 && lastFence > firstLineEnd
                ? trimmed.substring(firstLineEnd + 1, lastFence).trim()
                : trimmed;
    }
}
