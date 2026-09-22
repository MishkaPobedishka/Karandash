package ru.karandash.core.telegram;

import org.springframework.stereotype.Component;
import ru.karandash.contracts.telegram.ReplyButton;
import ru.karandash.contracts.telegram.TelegramReply;
import ru.karandash.core.reminder.Reminder;
import ru.karandash.core.reminder.ReminderService;
import ru.karandash.core.reminder.ReminderType;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static ru.karandash.core.reminder.ReminderService.format;

/**
 * Экраны напоминаний про еду. Всё живёт в одном сообщении: список — карточка приёма пищи — часовой пояс,
 * переключение идёт заменой текста и кнопок.
 */
@Component
public class ReminderDialog {

    /** Кнопки сдвига времени стоят в один ряд: иначе экран растягивается на полтелефона. */
    private static final int SHIFT_ROW = 1;
    private static final List<Integer> SHIFTS = List.of(-60, -10, 10, 60);

    private final ReminderService reminders;

    public ReminderDialog(ReminderService reminders) {
        this.reminders = reminders;
    }

    /** Кнопка, которой открывают напоминания из приветствия, подсказки и самого напоминания. */
    public ReplyButton settingsButton() {
        return new DialogCallback(DialogCallback.Action.REMINDERS).button(TelegramTexts.BUTTON_REMINDERS);
    }

    /** Текст самого напоминания: «пора записать завтрак». */
    public String dueText(ReminderType type) {
        return TelegramTexts.REMINDER_DUE.formatted(type.emoji(), type.title().toLowerCase(java.util.Locale.ROOT));
    }

    /** Под напоминанием — настройка, а под обедом ещё и меню столовой. */
    public List<ReplyButton> dueButtons(ReminderType type) {
        List<ReplyButton> buttons = new ArrayList<>();
        if (type == ReminderType.LUNCH) {
            buttons.add(new DialogCallback(DialogCallback.Action.LUNCH_SHOW).button(TelegramTexts.BUTTON_LUNCH_MENU));
        }
        buttons.add(settingsButton());
        return buttons;
    }

    /** Главный экран: три напоминания, время каждого и часовой пояс. */
    public TelegramReply menu(UUID accountId) {
        ZoneId zone = reminders.zone(accountId);
        StringBuilder text = new StringBuilder(TelegramTexts.REMINDERS_TITLE).append("\n");
        List<ReplyButton> buttons = new ArrayList<>();
        for (Reminder reminder : reminders.settings(accountId)) {
            text.append('\n').append(line(reminder));
            buttons.add(mealButton(reminder));
        }
        text.append("\n\n").append(TelegramTexts.REMINDERS_ZONE.formatted(zoneName(zone), now(zone)));
        buttons.add(new DialogCallback(DialogCallback.Action.REMINDER_ZONE)
                .button(TelegramTexts.BUTTON_REMINDER_ZONE.formatted(zoneName(zone))));
        return TelegramReply.screen(text.toString(), buttons);
    }

    /** Экран одного приёма пищи: сдвиг времени и выключатель. */
    public TelegramReply meal(UUID accountId, ReminderType type) {
        Reminder reminder = reminders.setting(accountId, type);
        return TelegramReply.screen(card(reminder, reminders.zone(accountId)), mealButtons(reminder));
    }

    public TelegramReply shift(UUID accountId, ReminderType type, int minutes) {
        Reminder reminder = reminders.shift(accountId, type, minutes);
        return TelegramReply.screen(card(reminder, reminders.zone(accountId)), mealButtons(reminder));
    }

    public TelegramReply toggle(UUID accountId, ReminderType type) {
        Reminder reminder = reminders.toggle(accountId, type);
        return TelegramReply.screen(card(reminder, reminders.zone(accountId)), mealButtons(reminder));
    }

    /** Экран выбора часового пояса. */
    public TelegramReply zones(UUID accountId) {
        ZoneId current = reminders.zone(accountId);
        List<ReplyButton> buttons = new ArrayList<>();
        for (ZoneId zone : ReminderService.ZONES) {
            String mark = zone.equals(current) ? "✅ " : "";
            buttons.add(new DialogCallback(DialogCallback.Action.REMINDER_ZONE_SET, zoneCode(zone))
                    .button(mark + zoneName(zone) + " — " + now(zone)));
        }
        buttons.add(backButton());
        return TelegramReply.screen(TelegramTexts.REMINDER_ZONE_PICK.formatted(zoneName(current)), buttons);
    }

    public TelegramReply setZone(UUID accountId, String code) {
        ReminderService.ZONES.stream()
                .filter(zone -> zoneCode(zone).equals(code))
                .findFirst()
                .ifPresent(zone -> reminders.setZone(accountId, zone));
        return menu(accountId);
    }

    private List<ReplyButton> mealButtons(Reminder reminder) {
        List<ReplyButton> buttons = new ArrayList<>();
        for (int minutes : SHIFTS) {
            buttons.add(new DialogCallback(DialogCallback.Action.REMINDER_SHIFT,
                    reminder.type().code() + ":" + minutes)
                    .button(shiftLabel(minutes)).inRow(SHIFT_ROW));
        }
        buttons.add(new DialogCallback(DialogCallback.Action.REMINDER_TOGGLE, reminder.type().code())
                .button(reminder.enabled() ? TelegramTexts.BUTTON_REMINDER_OFF : TelegramTexts.BUTTON_REMINDER_ON));
        buttons.add(backButton());
        return buttons;
    }

    private static ReplyButton backButton() {
        return new DialogCallback(DialogCallback.Action.REMINDERS).button(TelegramTexts.BUTTON_BACK);
    }

    private ReplyButton mealButton(Reminder reminder) {
        return new DialogCallback(DialogCallback.Action.REMINDER_MEAL, reminder.type().code())
                .button("%s %s — %s %s".formatted(reminder.type().emoji(), reminder.type().title(),
                        format(reminder.at()), mark(reminder)));
    }

    private static String line(Reminder reminder) {
        return "%s %s — %s %s".formatted(reminder.type().emoji(), reminder.type().title(),
                format(reminder.at()), reminder.enabled() ? "" : TelegramTexts.REMINDER_OFF_MARK).strip();
    }

    private static String card(Reminder reminder, ZoneId zone) {
        String state = reminder.enabled()
                ? TelegramTexts.REMINDER_CARD_ON.formatted(format(reminder.at()))
                : TelegramTexts.REMINDER_CARD_OFF;
        return "%s %s\n\n%s\n%s".formatted(reminder.type().emoji(), reminder.type().title(), state,
                TelegramTexts.REMINDERS_ZONE.formatted(zoneName(zone), now(zone)));
    }

    private static String mark(Reminder reminder) {
        return reminder.enabled() ? "✅" : "❌";
    }

    private static String shiftLabel(int minutes) {
        String sign = minutes < 0 ? "−" : "+";
        int value = Math.abs(minutes);
        return value >= 60 ? sign + (value / 60) + " ч" : sign + value + " мин";
    }

    /** Город понятнее, чем «Asia/Yekaterinburg». */
    private static String zoneName(ZoneId zone) {
        return switch (zone.getId()) {
            case "Europe/Moscow" -> "Москва";
            case "Asia/Yekaterinburg" -> "Тюмень";
            case "Asia/Novosibirsk" -> "Новосибирск";
            case "Asia/Almaty" -> "Алматы";
            default -> zone.getId();
        };
    }

    /** Короткий код пояса для кнопки: полное имя в callback_data не помещается. */
    private static String zoneCode(ZoneId zone) {
        return switch (zone.getId()) {
            case "Europe/Moscow" -> "msk";
            case "Asia/Yekaterinburg" -> "tmn";
            case "Asia/Novosibirsk" -> "nsk";
            case "Asia/Almaty" -> "ala";
            default -> zone.getId();
        };
    }

    private static String now(ZoneId zone) {
        return format(ZonedDateTime.now(zone).toLocalTime());
    }
}
