package ru.karandash.core.canteen;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.karandash.core.prefs.Preferences;

import java.util.UUID;

/**
 * Кому утром приходит подбор обеда. По умолчанию приходит всем, у кого открыт доступ:
 * отказаться можно командой /lunch.
 */
@Service
public class LunchSubscriptions {

    private static final String TYPE = "CANTEEN_LUNCH";
    private static final String ON = "on";
    private static final String OFF = "off";

    private final Preferences preferences;

    public LunchSubscriptions(Preferences preferences) {
        this.preferences = preferences;
    }

    @Transactional(readOnly = true)
    public boolean enabled(UUID accountId) {
        return preferences.find(accountId, TYPE).map(ON::equals).orElse(true);
    }

    @Transactional
    public boolean toggle(UUID accountId) {
        boolean next = !enabled(accountId);
        set(accountId, next);
        return next;
    }

    @Transactional
    public void set(UUID accountId, boolean enabled) {
        preferences.set(accountId, TYPE, enabled ? ON : OFF);
    }
}
