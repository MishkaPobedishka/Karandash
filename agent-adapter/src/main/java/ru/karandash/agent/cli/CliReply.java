package ru.karandash.agent.cli;

import ru.karandash.contracts.ai.ModelUsage;

public record CliReply(
        String text,
        ModelUsage usage
) {
}
