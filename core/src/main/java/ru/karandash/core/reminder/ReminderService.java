package ru.karandash.core.reminder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.karandash.core.diary.DiaryProperties;
import ru.karandash.core.prefs.Preferences;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Напоминания про еду: во сколько напомнить про завтрак, обед и ужин и в каком часовом поясе.
 * Пока человек ничего не менял, строк в базе нет и работают значения по умолчанию — напоминания включены.
 */
@Service
public class ReminderService {

    /** Часовые пояса, между которыми можно переключаться кнопкой: все, где живут пользователи бота. */
    public static final List<ZoneId> ZONES = List.of(
            ZoneId.of("Europe/Moscow"),
            ZoneId.of("Asia/Yekaterinburg"),
            ZoneId.of("Asia/Novosibirsk"),
            ZoneId.of("Asia/Almaty"));

    private static final String ZONE_PREFERENCE = "TIME_ZONE";

    private final ReminderRepository reminders;
    private final Preferences preferences;
    private final ZoneId defaultZone;

    public ReminderService(ReminderRepository reminders, Preferences preferences, DiaryProperties diaryProperties) {
        this.reminders = reminders;
        this.preferences = preferences;
        this.defaultZone = diaryProperties.zone();
    }

    /** Все три напоминания человека — из базы, а чего там нет, берём по умолчанию. */
    @Transactional(readOnly = true)
    public List<Reminder> settings(UUID accountId) {
        List<ReminderEntity> saved = reminders.findByAccountId(accountId);
        List<Reminder> result = new ArrayList<>();
        for (ReminderType type : ReminderType.values()) {
            result.add(saved.stream()
                    .filter(entity -> entity.getType() == type)
                    .findFirst()
                    .map(entity -> new Reminder(type, entity.getLocalTime(), entity.isEnabled()))
                    .orElseGet(() -> new Reminder(type, type.defaultTime(), true)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Reminder setting(UUID accountId, ReminderType type) {
        return reminders.findByAccountIdAndType(accountId, type)
                .map(entity -> new Reminder(type, entity.getLocalTime(), entity.isEnabled()))
                .orElseGet(() -> new Reminder(type, type.defaultTime(), true));
    }

    /** Часовой пояс человека: пока не выбрал — тот же, в котором считается день дневника. */
    @Transactional(readOnly = true)
    public ZoneId zone(UUID accountId) {
        return preferences.find(accountId, ZONE_PREFERENCE).flatMap(ReminderService::parseZone).orElse(defaultZone);
    }

    @Transactional
    public void setZone(UUID accountId, ZoneId zone) {
        preferences.set(accountId, ZONE_PREFERENCE, zone.getId());
    }

    @Transactional
    public Reminder toggle(UUID accountId, ReminderType type) {
        ReminderEntity entity = entity(accountId, type);
        entity.setEnabled(!entity.isEnabled());
        reminders.saveAndFlush(entity);
        return new Reminder(type, entity.getLocalTime(), entity.isEnabled());
    }

    /** Сдвигает время напоминания; через полночь время просто заворачивается на начало суток. */
    @Transactional
    public Reminder shift(UUID accountId, ReminderType type, int minutes) {
        ReminderEntity entity = entity(accountId, type);
        entity.setLocalTime(entity.getLocalTime().plusMinutes(minutes));
        reminders.saveAndFlush(entity);
        return new Reminder(type, entity.getLocalTime(), entity.isEnabled());
    }

    /**
     * Отмечает, что напоминание за этот день отправлено.
     *
     * @return {@code false}, если его уже отправляли сегодня — тогда второй раз не шлём
     */
    @Transactional
    public boolean claim(UUID accountId, ReminderType type, LocalDate today) {
        ReminderEntity entity = entity(accountId, type);
        if (today.equals(entity.getLastSentOn())) {
            return false;
        }
        entity.setLastSentOn(today);
        reminders.saveAndFlush(entity);
        return true;
    }

    private ReminderEntity entity(UUID accountId, ReminderType type) {
        return reminders.findByAccountIdAndType(accountId, type)
                .orElseGet(() -> reminders.saveAndFlush(
                        new ReminderEntity(accountId, type, type.defaultTime(), true)));
    }

    /** Часовой пояс мог остаться от старой настройки или исчезнуть из базы зон — тогда берём свой. */
    private static Optional<ZoneId> parseZone(String value) {
        try {
            return Optional.of(ZoneId.of(value));
        } catch (DateTimeException exception) {
            return Optional.empty();
        }
    }

    /** Время в кнопке и в тексте — всегда в часовом поясе человека. */
    public static String format(LocalTime time) {
        return "%02d:%02d".formatted(time.getHour(), time.getMinute());
    }
}
