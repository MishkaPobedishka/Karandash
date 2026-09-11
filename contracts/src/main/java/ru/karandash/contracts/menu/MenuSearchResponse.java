package ru.karandash.contracts.menu;

import java.time.Instant;
import java.util.List;

public record MenuSearchResponse(
        MenuSource source,
        String zone,
        Instant fetchedAt,
        boolean stub,
        List<MenuItemDto> items
) {
    public static MenuSearchResponse emptyStub(MenuSource source, String zone) {
        return new MenuSearchResponse(source, zone, Instant.now(), true, List.of());
    }
}

