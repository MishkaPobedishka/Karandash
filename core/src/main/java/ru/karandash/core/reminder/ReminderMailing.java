package ru.karandash.core.reminder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.karandash.core.account.AccessService;
import ru.karandash.core.account.AccountAccess;
import ru.karandash.core.diary.DiaryDay;
import ru.karandash.core.diary.DiaryService;
import ru.karandash.core.telegram.EveningSummary;
import ru.karandash.core.telegram.ReminderDialog;
import ru.karandash.core.telegram.TelegramNotifications;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Раз в минуту смотрит, кому пора напомнить про еду. Время у каждого своё и в своём часовом поясе.
 * Если человек уже записал еду незадолго до срока, напоминание пропускается — он и так помнит.
 */
@Component
public class ReminderMailing {

    private static final Logger log = LoggerFactory.getLogger(ReminderMailing.class);
    /** Насколько позже срока ещё уместно напомнить: после перезапуска сервиса не будим людей задним числом. */
    private static final Duration NOT_LATER_THAN = Duration.ofHours(2);
    /** Записал еду в этом окне вокруг срока — значит, напоминать не о чем. */
    private static final Duration ALREADY_EATEN_WINDOW = Duration.ofMinutes(90);

    private final AccessService access;
    private final ReminderService reminders;
    private final DiaryService diary;
    private final ReminderDialog dialog;
    private final EveningSummary summary;
    private final TelegramNotifications notifications;

    public ReminderMailing(
            AccessService access,
            ReminderService reminders,
            DiaryService diary,
            ReminderDialog dialog,
            EveningSummary summary,
            TelegramNotifications notifications
    ) {
        this.access = access;
        this.reminders = reminders;
        this.diary = diary;
        this.dialog = dialog;
        this.summary = summary;
        this.notifications = notifications;
    }

    @Scheduled(fixedDelayString = "${karandash.reminders.check-interval:60000}")
    public void sendDueReminders() {
        int sent = 0;
        for (AccountAccess account : access.allowed()) {
            ZoneId zone = reminders.zone(account.accountId());
            ZonedDateTime now = ZonedDateTime.now(zone);
            for (Reminder reminder : reminders.settings(account.accountId())) {
                if (!due(reminder, now.toLocalTime())) {
                    continue;
                }
                // Сводка приходит в любом случае: она подводит итог дня, а не зовёт записать еду.
                if (!reminder.type().isSummary() && alreadyEaten(account, reminder, now.toLocalDate())) {
                    continue;
                }
                if (!reminders.claim(account.accountId(), reminder.type(), now.toLocalDate())) {
                    continue;
                }
                String text = reminder.type().isSummary()
                        ? summary.text(account.accountId(), now.toLocalDate())
                        : dialog.dueText(reminder.type());
                notifications.notify(account.accountId(), account.telegramId(), text,
                        dialog.dueButtons(reminder.type()));
                sent++;
            }
        }
        if (sent > 0) {
            log.info("Напоминания про еду: отправлено {}", sent);
        }
    }

    private static boolean due(Reminder reminder, LocalTime now) {
        if (!reminder.enabled() || now.isBefore(reminder.at())) {
            return false;
        }
        return Duration.between(reminder.at(), now).compareTo(NOT_LATER_THAN) <= 0;
    }

    /** Еда, записанная незадолго до срока или после него, — повод промолчать. */
    private boolean alreadyEaten(AccountAccess account, Reminder reminder, LocalDate today) {
        DiaryDay day = diary.today(account.accountId());
        if (!day.date().equals(today)) {
            return false;
        }
        LocalTime from = reminder.at().minusMinutes(ALREADY_EATEN_WINDOW.toMinutes());
        // У раннего напоминания окно уехало бы во вчерашний вечер — тогда считаем с начала суток.
        LocalTime since = from.isAfter(reminder.at()) ? LocalTime.MIN : from;
        return day.entries().stream().anyMatch(entry -> !entry.time().isBefore(since));
    }
}
