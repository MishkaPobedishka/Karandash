package ru.karandash.contracts.ai;

/**
 * Запрос к агент-адаптеру на распознавание текстового описания еды.
 *
 * @param context справка от ядра — например, сегодняшнее меню столовой; не пользовательский ввод
 */
public record AgentTextRequest(
        String description,
        String context
) {

    public AgentTextRequest(String description) {
        this(description, null);
    }
}
