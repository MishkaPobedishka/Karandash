package ru.karandash.agent.cli;

import java.time.Duration;

public record ProcessOutcome(
        int exitCode,
        String stderrExcerpt,
        Duration duration
) {
}
