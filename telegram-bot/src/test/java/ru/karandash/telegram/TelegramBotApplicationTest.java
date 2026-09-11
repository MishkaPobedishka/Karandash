package ru.karandash.telegram;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import ru.karandash.telegram.polling.UpdatePoller;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Приёмщик поднимается без базы данных: в контексте нет ни одного DataSource.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "karandash.telegram.token=",
        "karandash.telegram.notifications-enabled=false"
})
class TelegramBotApplicationTest {

    @Autowired
    ApplicationContext context;

    @Autowired
    TestRestTemplate rest;

    @Test
    void startsStatelessWithoutDatabase() {
        assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
        assertThat(context.getBean(UpdatePoller.class).isRunning()).as("без токена опрос выключен").isFalse();
        assertThat(rest.getForEntity("/actuator/health/readiness", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
}
