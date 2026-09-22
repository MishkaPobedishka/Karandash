package ru.karandash.contracts.ai;

/**
 * Запрос к агент-адаптеру на подбор обеда: меню и рамки, собранные ядром.
 */
public record AgentLunchRequest(String prompt) {
}
