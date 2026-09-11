package ru.karandash.contracts.menu;

public record AdapterStatusResponse(MenuSource source, boolean stub, String message) {

    public static AdapterStatusResponse stub(MenuSource source) {
        return new AdapterStatusResponse(source, true, "Интеграция ещё не подключена");
    }
}

