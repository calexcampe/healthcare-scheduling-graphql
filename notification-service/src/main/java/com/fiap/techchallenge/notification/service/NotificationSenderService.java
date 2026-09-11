package com.fiap.techchallenge.notification.service;

import com.fiap.techchallenge.notification.event.AppointmentChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Simulates sending a reminder to the patient. A real implementation would
 * call an email/SMS provider here; that integration is out of scope for this
 * challenge, so we log the "sent" reminder instead.
 */
@Service
public class NotificationSenderService {

    private static final Logger log = LoggerFactory.getLogger(NotificationSenderService.class);

    public void sendReminder(AppointmentChangedEvent event) {
        log.info("Lembrete enviado para {} (consulta {} em {}) - evento: {}",
                event.patientEmail(), event.appointmentId(), event.appointmentDateTime(), event.eventType());
    }
}
