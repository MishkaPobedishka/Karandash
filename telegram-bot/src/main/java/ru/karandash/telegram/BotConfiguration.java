package ru.karandash.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import ru.karandash.telegram.api.TelegramApiClient;
import ru.karandash.telegram.core.CoreClient;
import ru.karandash.telegram.polling.MessageHandler;
import ru.karandash.telegram.polling.PollingState;
import ru.karandash.telegram.polling.UpdatePoller;
import ru.karandash.telegram.polling.UpdateProcessor;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
@EnableConfigurationProperties({TelegramProperties.class, CoreProperties.class})
public class BotConfiguration {

    @Bean
    TelegramApiClient telegramApiClient(
            TelegramProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper
    ) {
        // Long polling держит соединение до poll-timeout — таймаут чтения должен быть заметно больше.
        ClientHttpRequestFactorySettings timeouts = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(10))
                .withReadTimeout(properties.pollTimeout().plusSeconds(15));
        RestClient restClient = restClientBuilder
                .baseUrl(properties.apiBaseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(timeouts))
                .build();
        return new TelegramApiClient(restClient, properties.token() == null ? "" : properties.token(), objectMapper);
    }

    @Bean
    CoreClient coreClient(CoreProperties properties, RestClient.Builder restClientBuilder) {
        ClientHttpRequestFactorySettings timeouts = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());
        RestClient restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeaders(headers -> {
                    if (properties.serviceToken() != null && !properties.serviceToken().isBlank()) {
                        headers.setBearerAuth(properties.serviceToken());
                    }
                })
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(timeouts))
                .build();
        return new CoreClient(restClient);
    }

    @Bean(destroyMethod = "shutdownNow")
    ScheduledExecutorService typingScheduler() {
        return Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("telegram-typing").factory());
    }

    @Bean(destroyMethod = "shutdownNow")
    ExecutorService chatExecutor(TelegramProperties properties) {
        return Executors.newFixedThreadPool(properties.parallelChats(),
                Thread.ofVirtual().name("telegram-chat-", 0).factory());
    }

    @Bean
    PollingState pollingState() {
        return new PollingState();
    }

    @Bean
    MessageHandler messageHandler(
            TelegramApiClient telegram,
            CoreClient core,
            ScheduledExecutorService typingScheduler,
            TelegramProperties properties
    ) {
        return new MessageHandler(telegram, core, typingScheduler, properties.maxPhotoSize().toBytes());
    }

    @Bean
    UpdatePoller updatePoller(
            TelegramApiClient telegram,
            MessageHandler handler,
            ExecutorService chatExecutor,
            PollingState pollingState,
            TelegramProperties properties
    ) {
        return new UpdatePoller(telegram, new UpdateProcessor(handler, chatExecutor), pollingState,
                properties.pollTimeout(), properties.pollingEnabled());
    }
}
