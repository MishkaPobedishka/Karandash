package ru.karandash.contracts.menu;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum MenuSource {
    TAVERN("tavern", "sd-menu"),
    YANDEX_LAVKA("yandex-lavka"),
    SAMOKAT("samokat"),
    LIFEMART("lifemart");

    private final String externalName;
    private final String[] aliases;

    MenuSource(String externalName, String... aliases) {
        this.externalName = externalName;
        this.aliases = aliases;
    }

    @JsonValue
    public String externalName() {
        return externalName;
    }

    @JsonCreator
    public static MenuSource fromExternalName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Источник меню не задан");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (MenuSource source : values()) {
            if (source.externalName.equals(normalized)) {
                return source;
            }
            for (String alias : source.aliases) {
                if (alias.equals(normalized)) {
                    return source;
                }
            }
        }
        throw new IllegalArgumentException("Неизвестный источник меню: " + value);
    }
}
