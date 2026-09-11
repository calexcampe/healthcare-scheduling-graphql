package com.fiap.techchallenge.notification.event;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Mirrors the contract published by scheduling-service on the
 * "appointments.exchange" / "appointment.changed" routing key. Kept as a
 * plain, independent DTO (no shared library) since the two services are
 * meant to evolve and deploy independently.
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
