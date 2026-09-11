package ru.karandash.contracts.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MenuSourceTest {

    @Test
    void acceptsLegacySdMenuAlias() {
        assertEquals(MenuSource.TAVERN, MenuSource.fromExternalName("sd-menu"));
    }

    @Test
    void usesCanonicalNames() {
        assertEquals("tavern", MenuSource.TAVERN.externalName());
        assertEquals("yandex-lavka", MenuSource.YANDEX_LAVKA.externalName());
    }
}

