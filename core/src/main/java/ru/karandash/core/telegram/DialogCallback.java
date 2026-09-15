package ru.karandash.core.telegram;

import ru.karandash.contracts.telegram.ReplyButton;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Данные кнопки под оценкой. В Telegram уходит только действие и идентификатор черновика —
 * ни текста пользователя, ни чисел оценки там нет, а сам черновик всё равно ищется по аккаунту.
 */
record DialogCallback(Action action, UUID draftId) {

    enum Action {
        /** Записать оценку в дневник. */
        SAVE("save"),
        /** Забыть оценку. */
        DROP("drop"),
        /** Уточнить нечем — пусть модель прикинет по типичному варианту. */
        AS_IS("asis");

        private final String code;

        Action(String code) {
            this.code = code;
        }

        String code() {
            return code;
        }

        static Optional<Action> byCode(String code) {
            for (Action action : values()) {
                if (action.code.equals(code)) {
                    return Optional.of(action);
                }
            }
            return Optional.empty();
        }
    }

    String data() {
        String data = action.code() + ":" + draftId;
        if (data.getBytes(StandardCharsets.UTF_8).length > ReplyButton.MAX_DATA_BYTES) {
            throw new IllegalStateException("Данные кнопки не помещаются в ограничение Telegram");
        }
        return data;
    }

    ReplyButton button(String text) {
        return new ReplyButton(text, data());
    }

    static Optional<DialogCallback> parse(String data) {
        if (data == null) {
            return Optional.empty();
        }
        int separator = data.indexOf(':');
        if (separator <= 0) {
            return Optional.empty();
        }
        Optional<Action> action = Action.byCode(data.substring(0, separator));
        if (action.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new DialogCallback(action.get(), UUID.fromString(data.substring(separator + 1))));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
