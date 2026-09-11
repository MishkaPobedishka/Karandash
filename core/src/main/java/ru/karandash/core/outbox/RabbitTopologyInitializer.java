package ru.karandash.core.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Объявляет обменники и очереди сразу после старта. Без этого RabbitAdmin делает это лениво — при первой
 * публикации события, и до тех пор приёмщику не на что подписаться. Если брокер недоступен, топология
 * будет объявлена при первом подключении, как раньше.
 */
@Component
class RabbitTopologyInitializer {

    private static final Logger log = LoggerFactory.getLogger(RabbitTopologyInitializer.class);

    private final AmqpAdmin amqpAdmin;

    RabbitTopologyInitializer(AmqpAdmin amqpAdmin) {
        this.amqpAdmin = amqpAdmin;
    }

    @EventListener(ApplicationReadyEvent.class)
    void declareTopology() {
        if (!(amqpAdmin instanceof RabbitAdmin rabbitAdmin)) {
            return;
        }
        try {
            rabbitAdmin.initialize();
        } catch (AmqpException exception) {
            log.warn("RabbitMQ недоступен при старте — топология будет объявлена при первом подключении: {}",
                    exception.getClass().getSimpleName());
        }
    }
}
