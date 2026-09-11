package ru.karandash.core.ai;

/**
 * Провайдер модели распознавания, {@code karandash.ai.provider}.
 */
public enum AiProvider {
    DISABLED("disabled"),
    OPENAI_COMPATIBLE("openai-compatible"),
    AGENT("agent");

    private final String configName;

    AiProvider(String configName) {
        this.configName = configName;
    }

    public String configName() {
        return configName;
    }
}
