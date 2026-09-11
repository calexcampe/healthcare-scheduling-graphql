package com.fiap.techchallenge.scheduling.messaging.event;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Payload published to RabbitMQ when an appointment is created or updated.
 * Intentionally decoupled from the JPA entity: only the data the
 * notification-service needs to send a reminder.
 */
public record AppointmentChangedEvent(
        Long appointmentId,
        Long patientId,
        String patientEmail,
        String patientName,
        LocalDateTime appointmentDateTime,
        AppointmentEventType eventType
) implements Serializable {
}
