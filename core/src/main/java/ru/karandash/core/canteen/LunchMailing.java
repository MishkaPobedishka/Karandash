package ru.karandash.core.canteen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.karandash.core.account.AccessService;
import ru.karandash.core.account.AccountAccess;
import ru.karandash.core.telegram.LunchReplyFormatter;
import ru.karandash.core.telegram.TelegramNotifications;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Утренняя рассылка: раз в рабочий день берём меню столовой и каждому подписчику подбираем обед
 * под его норму и то, что он уже съел. Меню запрашивается один раз на всех, модель — по разу на человека.
 */
@Component
public class LunchMailing {

    private static final Logger log = LoggerFactory.getLogger(LunchMailing.class);

    private final CanteenClient canteen;
    private final CanteenProperties properties;
    private final LunchAdvisor advisor;
    private final LunchSubscriptions subscriptions;
    private final LunchContext context;
    private final LunchReplyFormatter formatter;
    private final AccessService access;
    private final TelegramNotifications notifications;

    public LunchMailing(
            CanteenClient canteen,
            CanteenProperties properties,
            LunchAdvisor advisor,
            LunchSubscriptions subscriptions,
            LunchContext context,
            LunchReplyFormatter formatter,
            AccessService access,
            TelegramNotifications notifications
    ) {
        this.canteen = canteen;
        this.properties = properties;
        this.advisor = advisor;
        this.subscriptions = subscriptions;
        this.context = context;
        this.formatter = formatter;
        this.access = access;
        this.notifications = notifications;
    }

    @Scheduled(cron = "${karandash.canteen.cron:0 0 10 * * MON-FRI}", zone = "${karandash.canteen.zone:Europe/Moscow}")
    public void sendDailyAdvice() {
        if (!properties.workday()) {
            log.info("Рассылка обеда пропущена: выходной, столовая ЦО не работает");
            return;
        }
        Optional<CanteenMenu> menu = canteen.today();
        if (menu.isEmpty()) {
            log.info("Рассылка обеда пропущена: меню на сегодня нет");
            return;
        }
        long startedAt = System.nanoTime();
        int sent = 0;
        for (AccountAccess account : access.allowed()) {
            if (!subscriptions.enabled(account.accountId())) {
                continue;
            }
            Optional<LunchSuggestion> suggestion = context.suggest(account.accountId(), menu.get());
            if (suggestion.isEmpty()) {
                continue;
            }
            notifications.notify(account.accountId(), account.telegramId(),
                    formatter.mailing(suggestion.get(), context.remaining(account.accountId())),
                    List.of(formatter.menuButton()));
            sent++;
        }
        log.info("Рассылка обеда: отправлено {} сообщений за {} мс", sent,
                Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
    }
}
