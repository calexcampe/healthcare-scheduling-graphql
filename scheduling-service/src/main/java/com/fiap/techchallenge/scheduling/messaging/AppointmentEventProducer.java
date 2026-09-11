package com.fiap.techchallenge.scheduling.messaging;

import com.fiap.techchallenge.scheduling.config.RabbitMQConfig;
import com.fiap.techchallenge.scheduling.messaging.event.AppointmentChangedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class AppointmentEventProducer {

    private final RabbitTemplate rabbitTemplate;

    public AppointmentEventProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(AppointmentChangedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.APPOINTMENTS_EXCHANGE,
                RabbitMQConfig.APPOINTMENT_CHANGED_ROUTING_KEY,
                event);
    }
}
