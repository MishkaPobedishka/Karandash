package ru.karandash.core.outbox;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfiguration {

    static final String EVENTS_EXCHANGE = "core.events";

    @Bean
    TopicExchange coreEventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange("core.dlx", true, false);
    }

    @Bean
    Queue botEventsQueue() {
        return QueueBuilder.durable("q.bot.events")
                .deadLetterExchange("core.dlx")
                .deadLetterRoutingKey("q.bot.events.dead")
                .build();
    }

    @Bean
    Queue botEventsDeadQueue() {
        return QueueBuilder.durable("q.bot.events.dead").build();
    }

    @Bean
    Binding botEventsBinding(Queue botEventsQueue, TopicExchange coreEventsExchange) {
        // Приёмщику — только события для Telegram: остальные доменные события не должны уходить на зарубежную ноду.
        return BindingBuilder.bind(botEventsQueue).to(coreEventsExchange).with("telegram.#");
    }

    @Bean
    Binding botEventsDeadBinding(Queue botEventsDeadQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(botEventsDeadQueue)
                .to(deadLetterExchange)
                .with("q.bot.events.dead");
    }
}
