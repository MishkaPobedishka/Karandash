package ru.karandash.core.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * События outbox уходят в RabbitMQ JSON-ом. Без конвертера {@code JsonNode} уходил бы Java-сериализацией,
 * а её нельзя безопасно разбирать в приёмщике на другой ноде.
 */
@Configuration
public class OutboxMessageConverterConfiguration {

    @Bean
    MessageConverter outboxMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
