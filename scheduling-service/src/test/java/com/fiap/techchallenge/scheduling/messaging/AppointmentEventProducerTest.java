package com.fiap.techchallenge.scheduling.messaging;

import com.fiap.techchallenge.scheduling.config.RabbitMQConfig;
import com.fiap.techchallenge.scheduling.messaging.event.AppointmentChangedEvent;
import com.fiap.techchallenge.scheduling.messaging.event.AppointmentEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AppointmentEventProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private AppointmentEventProducer producer;

    @Test
    void publishesEventToAppointmentsExchangeWithChangedRoutingKey() {
        AppointmentChangedEvent event = new AppointmentChangedEvent(
                1L, 2L, "paciente@example.com", "Joao Silva", LocalDateTime.now().plusDays(1), AppointmentEventType.CREATED);

        producer.publish(event);

        verify(rabbitTemplate).convertAndSend(
                RabbitMQConfig.APPOINTMENTS_EXCHANGE,
                RabbitMQConfig.APPOINTMENT_CHANGED_ROUTING_KEY,
                event);
    }
}
