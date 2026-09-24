package ru.karandash.core.telegram;

import ru.karandash.contracts.telegram.ReplyButton;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Данные кнопки. В Telegram уходит только код действия и короткая нагрузка — идентификатор черновика,
 * номер пользователя или выбранный вариант. Ни текста пользователя, ни чисел оценки там нет,
 * а всё, что нагрузка называет, всё равно проверяется по аккаунту нажавшего.
 */
record DialogCallback(Action action, String payload) {

    /** Нагрузки нет, но формат «код:значение» одинаков для всех кнопок. */
    private static final String NO_PAYLOAD = "-";

    enum Action {
        /** Записать оценку в дневник. */
        SAVE("save"),
        /** Забыть оценку. */
        DROP("drop"),
        /** Уточнить нечем — пусть модель прикинет по типичному варианту. */
        AS_IS("asis"),
        /** Пользователь просит доступ к боту. */
        REQUEST_ACCESS("areq"),
        /** Начать разговор о телосложении. */
        PROFILE_START("pnew"),
        PROFILE_SEX("psex"),
        PROFILE_ACTIVITY("pact"),
        PROFILE_GOAL("pgoal"),
        PROFILE_CANCEL("pstop"),
        /** Панель администратора и её разделы. */
        ADMIN_PANEL("apanel"),
        ADMIN_REQUESTS("areqs"),
        ADMIN_USERS("ausers"),
        ADMIN_USER("auser"),
        ADMIN_CHANGELOG("aclog"),
        /** Администратор открывает доступ пользователю. */
        ACCESS_GRANT("agrant"),
        ACCESS_DENY("adeny"),
        ACCESS_REVOKE("arevoke"),
        ACCESS_PROMOTE("apromote"),
        /** Показать подбор обеда и меню столовой. */
        LUNCH_SHOW("lshow"),
        /** Показать всё меню целиком. */
        LUNCH_MENU("lmenu"),
        /** Включить или выключить утренний подбор обеда. */
        LUNCH_MAILING("lmail"),
        /** Главное меню и его разделы. */
        MENU("menu"),
        MENU_DIARY("mdiary"),
        MENU_PROFILE("mprof"),
        MENU_CHANGELOG("mclog"),
        /** Напоминания про еду: список, карточка приёма пищи, сдвиг времени, выключатель, часовой пояс. */
        REMINDERS("rmenu"),
        REMINDER_MEAL("rmeal"),
        REMINDER_SHIFT("rtime"),
        REMINDER_TOGGLE("rtgl"),
        REMINDER_ZONE("rzone"),
        REMINDER_ZONE_SET("rzset"),
        /** Администратор рассылает или удаляет запись «что нового». */
        CHANGELOG_SEND("clsend"),
        CHANGELOG_DROP("cldrop");

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

    /** Нагрузка вида «l:60» — приём пищи и сдвиг: двоеточий в коде больше одного не бывает. */
    Optional<String[]> pairPayload() {
        int separator = payload.indexOf(':');
        return separator <= 0 || separator == payload.length() - 1
                ? Optional.empty()
                : Optional.of(new String[]{payload.substring(0, separator), payload.substring(separator + 1)});
    }

    DialogCallback(Action action, UUID payload) {
        this(action, payload.toString());
    }

    DialogCallback(Action action) {
        this(action, NO_PAYLOAD);
    }

    String data() {
        String data = action.code() + ":" + payload;
        if (data.getBytes(StandardCharsets.UTF_8).length > ReplyButton.MAX_DATA_BYTES) {
            throw new IllegalStateException("Данные кнопки не помещаются в ограничение Telegram");
        }
        return data;
    }

    ReplyButton button(String text) {
        return new ReplyButton(text, data());
    }

    Optional<UUID> uuidPayload() {
        try {
            return Optional.of(UUID.fromString(payload));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    Optional<Long> numberPayload() {
        try {
            return Optional.of(Long.parseLong(payload));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    static Optional<DialogCallback> parse(String data) {
        if (data == null) {
            return Optional.empty();
        }
        int separator = data.indexOf(':');
        if (separator <= 0 || separator == data.length() - 1) {
            return Optional.empty();
        }
        String payload = data.substring(separator + 1);
        if (payload.length() > 40 || payload.chars().anyMatch(Character::isWhitespace)) {
            return Optional.empty();
        }
        return Action.byCode(data.substring(0, separator))
                .map(action -> new DialogCallback(action, payload));
    }
}
