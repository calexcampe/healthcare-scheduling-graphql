package com.fiap.techchallenge.notification.listener;

import com.fiap.techchallenge.notification.config.RabbitMQConfig;
import com.fiap.techchallenge.notification.event.AppointmentChangedEvent;
import com.fiap.techchallenge.notification.service.NotificationSenderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AppointmentEventListener {

    private final NotificationSenderService notificationSenderService;

    public AppointmentEventListener(NotificationSenderService notificationSenderService) {
        this.notificationSenderService = notificationSenderService;
    }

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATIONS_QUEUE)
    public void onAppointmentChanged(AppointmentChangedEvent event) {
        notificationSenderService.sendReminder(event);
    }
}
