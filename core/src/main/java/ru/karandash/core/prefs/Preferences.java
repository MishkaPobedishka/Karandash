package ru.karandash.core.prefs;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Настройки человека из общей таблицы предпочтений: подписка на подбор обеда, часовой пояс и прочее.
 * Одна строка на пару «аккаунт — вид настройки».
 */
@Service
public class Preferences {

    private final PreferenceRepository preferences;

    public Preferences(PreferenceRepository preferences) {
        this.preferences = preferences;
    }

    @Transactional(readOnly = true)
    public Optional<String> find(UUID accountId, String type) {
        return preferences.findByAccountIdAndType(accountId, type).map(PreferenceEntity::getValue);
    }

    @Transactional
    public void set(UUID accountId, String type, String value) {
        preferences.findByAccountIdAndType(accountId, type).ifPresentOrElse(
                preference -> {
                    preference.setValue(value);
                    preferences.saveAndFlush(preference);
                },
                () -> preferences.saveAndFlush(new PreferenceEntity(accountId, type, value)));
    }
}
