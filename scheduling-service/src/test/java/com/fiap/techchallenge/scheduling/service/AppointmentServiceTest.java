package com.fiap.techchallenge.scheduling.service;

import com.fiap.techchallenge.scheduling.domain.Appointment;
import com.fiap.techchallenge.scheduling.domain.AppointmentStatus;
import com.fiap.techchallenge.scheduling.domain.Patient;
import com.fiap.techchallenge.scheduling.graphql.dto.AppointmentInput;
import com.fiap.techchallenge.scheduling.messaging.AppointmentEventProducer;
import com.fiap.techchallenge.scheduling.messaging.event.AppointmentChangedEvent;
import com.fiap.techchallenge.scheduling.messaging.event.AppointmentEventType;
import com.fiap.techchallenge.scheduling.repository.AppointmentRepository;
import com.fiap.techchallenge.scheduling.repository.PatientRepository;
import com.fiap.techchallenge.scheduling.security.AuthenticatedUser;
import com.fiap.techchallenge.scheduling.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private AppointmentEventProducer eventProducer;
    @Mock
    private CurrentUser currentUser;

    @InjectMocks
    private AppointmentService appointmentService;

    private Patient patient;

    @BeforeEach
    void setUp() {
        patient = new Patient(1L, "Joao Silva", "joao@example.com");
    }

    @Test
    void patientCanReadOwnHistory() {
        when(currentUser.get()).thenReturn(new AuthenticatedUser("paciente1", "x", "PACIENTE", 1L));
        when(appointmentRepository.findByPatientId(1L)).thenReturn(List.of(sampleAppointment()));

        List<Appointment> result = appointmentService.getPatientHistory(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    void patientCannotReadAnotherPatientHistory() {
        when(currentUser.get()).thenReturn(new AuthenticatedUser("paciente1", "x", "PACIENTE", 1L));

        assertThatThrownBy(() -> appointmentService.getPatientHistory(2L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void medicoCanReadAnyPatientHistory() {
        when(currentUser.get()).thenReturn(new AuthenticatedUser("medico1", "x", "MEDICO", null));
        when(appointmentRepository.findByPatientId(2L)).thenReturn(List.of());

        List<Appointment> result = appointmentService.getPatientHistory(2L);

        assertThat(result).isEmpty();
    }

    @Test
    void createAppointmentSavesAndPublishesCreatedEvent() {
        AppointmentInput input = new AppointmentInput(1L, "Dr. Carlos", "2026-10-01T10:00:00", "Consulta", null);
        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> {
            Appointment a = invocation.getArgument(0);
            a.setId(10L);
            return a;
        });

        Appointment result = appointmentService.createAppointment(input);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getStatus()).isEqualTo(AppointmentStatus.AGENDADA);
        verify(eventProducer).publish(argThatEventType(AppointmentEventType.CREATED));
    }

    @Test
    void updateAppointmentSavesAndPublishesUpdatedEvent() {
        Appointment existing = sampleAppointment();
        AppointmentInput input = new AppointmentInput(null, "Dr. Novo", null, null, AppointmentStatus.REALIZADA);
        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Appointment result = appointmentService.updateAppointment(10L, input);

        assertThat(result.getProfessionalName()).isEqualTo("Dr. Novo");
        assertThat(result.getStatus()).isEqualTo(AppointmentStatus.REALIZADA);
        verify(eventProducer).publish(argThatEventType(AppointmentEventType.UPDATED));
    }

    private Appointment sampleAppointment() {
        Appointment appointment = new Appointment();
        appointment.setId(10L);
        appointment.setPatient(patient);
        appointment.setProfessionalName("Dr. Carlos");
        appointment.setDateTime(LocalDateTime.now().plusDays(1));
        appointment.setDescription("Consulta");
        appointment.setStatus(AppointmentStatus.AGENDADA);
        return appointment;
    }

    private AppointmentChangedEvent argThatEventType(AppointmentEventType type) {
        return org.mockito.ArgumentMatchers.argThat(event -> event.eventType() == type);
    }
}
