package ru.karandash.contracts.ai;

import java.util.List;

public record RecognitionResult(
        List<RecognitionItem> items,
        List<String> questions
) {
}

