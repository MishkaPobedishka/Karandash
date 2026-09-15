package ru.karandash.core.telegram;

import org.junit.jupiter.api.Test;
import ru.karandash.contracts.telegram.ReplyButton;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DialogCallbackTest {

    @Test
    void keepsWithinTelegramLimitAndSurvivesRoundTrip() {
        DialogCallback callback = new DialogCallback(DialogCallback.Action.SAVE, UUID.randomUUID());

        ReplyButton button = callback.button("✓ Записать в дневник");

        assertThat(button.data().getBytes(StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(ReplyButton.MAX_DATA_BYTES);
        assertThat(DialogCallback.parse(button.data())).contains(callback);
    }

    @Test
    void rejectsAnythingUnexpected() {
        assertThat(DialogCallback.parse(null)).isEmpty();
        assertThat(DialogCallback.parse("")).isEmpty();
        assertThat(DialogCallback.parse("save")).isEmpty();
        assertThat(DialogCallback.parse("save:не-uuid")).isEmpty();
        assertThat(DialogCallback.parse("drop:" + UUID.randomUUID() + " and more")).isEmpty();
        assertThat(DialogCallback.parse("delete:" + UUID.randomUUID())).isEmpty();
    }
}
