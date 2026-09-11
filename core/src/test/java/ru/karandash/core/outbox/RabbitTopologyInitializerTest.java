package ru.karandash.core.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitTopologyInitializerTest {

    @Test
    void declaresTopologyOnStartup() {
        RabbitAdmin admin = mock(RabbitAdmin.class);

        new RabbitTopologyInitializer(admin).declareTopology();

        verify(admin).initialize();
    }

    @Test
    void unavailableBrokerDoesNotBreakStartup() {
        RabbitAdmin admin = mock(RabbitAdmin.class);
        doThrow(new AmqpConnectException(new ConnectException("refused"))).when(admin).initialize();

        assertThatCode(() -> new RabbitTopologyInitializer(admin).declareTopology()).doesNotThrowAnyException();
    }
}
