package com.fiap.techchallenge.scheduling.graphql.dto;

import com.fiap.techchallenge.scheduling.domain.AppointmentStatus;

/**
 * dateTime is bound as ISO-8601 text (e.g. 2026-10-01T14:30:00) since the
 * GraphQL schema keeps it as a plain String scalar to avoid pulling in a
 * custom DateTime scalar for this scope.
 */
public record AppointmentInput(
        Long patientId,
        String professionalName,
        String dateTime,
        String description,
        AppointmentStatus status
) {
}
