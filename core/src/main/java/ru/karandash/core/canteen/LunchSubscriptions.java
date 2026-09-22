package ru.karandash.core.canteen;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final PreferenceRepository preferences;

    public LunchSubscriptions(PreferenceRepository preferences) {
        this.preferences = preferences;
    }

    @Transactional(readOnly = true)
    public boolean enabled(UUID accountId) {
        return preferences.findByAccountIdAndType(accountId, TYPE)
                .map(preference -> ON.equals(preference.getValue()))
                .orElse(true);
    }

    @Transactional
    public boolean toggle(UUID accountId) {
        boolean next = !enabled(accountId);
        set(accountId, next);
        return next;
    }

    @Transactional
    public void set(UUID accountId, boolean enabled) {
        String value = enabled ? ON : OFF;
        preferences.findByAccountIdAndType(accountId, TYPE).ifPresentOrElse(
                preference -> {
                    preference.setValue(value);
                    preferences.saveAndFlush(preference);
                },
                () -> preferences.saveAndFlush(new PreferenceEntity(accountId, TYPE, value)));
    }
}
