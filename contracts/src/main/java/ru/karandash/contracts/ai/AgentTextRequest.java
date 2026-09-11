package ru.karandash.contracts.ai;

/**
 * Запрос к агент-адаптеру на распознавание текстового описания еды.
 */
public record AgentTextRequest(
        String description
) {
}
