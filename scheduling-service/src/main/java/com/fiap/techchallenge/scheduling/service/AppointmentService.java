package com.fiap.techchallenge.scheduling.service;

import com.fiap.techchallenge.scheduling.domain.Appointment;
import com.fiap.techchallenge.scheduling.domain.AppointmentStatus;
import com.fiap.techchallenge.scheduling.domain.Patient;
import com.fiap.techchallenge.scheduling.exception.ResourceNotFoundException;
import com.fiap.techchallenge.scheduling.graphql.dto.AppointmentInput;
import com.fiap.techchallenge.scheduling.messaging.AppointmentEventProducer;
import com.fiap.techchallenge.scheduling.messaging.event.AppointmentChangedEvent;
import com.fiap.techchallenge.scheduling.messaging.event.AppointmentEventType;
import com.fiap.techchallenge.scheduling.repository.AppointmentRepository;
import com.fiap.techchallenge.scheduling.repository.PatientRepository;
import com.fiap.techchallenge.scheduling.security.AuthenticatedUser;
import com.fiap.techchallenge.scheduling.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final AppointmentEventProducer eventProducer;
    private final CurrentUser currentUser;

    public AppointmentService(AppointmentRepository appointmentRepository,
                               PatientRepository patientRepository,
                               AppointmentEventProducer eventProducer,
                               CurrentUser currentUser) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.eventProducer = eventProducer;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<Appointment> getPatientHistory(Long patientId) {
        assertCanReadPatient(patientId);
        return appointmentRepository.findByPatientId(patientId);
    }

    @Transactional(readOnly = true)
    public List<Appointment> getFutureAppointments(Long patientId) {
        assertCanReadPatient(patientId);
        return appointmentRepository.findByPatientIdAndDateTimeAfter(patientId, LocalDateTime.now());
    }

    @PreAuthorize("hasAnyRole('MEDICO', 'ENFERMEIRO')")
    @Transactional
    public Appointment createAppointment(AppointmentInput input) {
        Patient patient = patientRepository.findById(input.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente nao encontrado: " + input.patientId()));

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setProfessionalName(input.professionalName());
        appointment.setDateTime(LocalDateTime.parse(input.dateTime()));
        appointment.setDescription(input.description());
        appointment.setStatus(input.status() != null ? input.status() : AppointmentStatus.AGENDADA);

        Appointment saved = appointmentRepository.save(appointment);
        publishEvent(saved, AppointmentEventType.CREATED);
        return saved;
    }

    @PreAuthorize("hasAnyRole('MEDICO', 'ENFERMEIRO')")
    @Transactional
    public Appointment updateAppointment(Long id, AppointmentInput input) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consulta nao encontrada: " + id));

        if (input.patientId() != null && !input.patientId().equals(appointment.getPatient().getId())) {
            Patient patient = patientRepository.findById(input.patientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Paciente nao encontrado: " + input.patientId()));
            appointment.setPatient(patient);
        }
        if (input.professionalName() != null) {
            appointment.setProfessionalName(input.professionalName());
        }
        if (input.dateTime() != null) {
            appointment.setDateTime(LocalDateTime.parse(input.dateTime()));
        }
        if (input.description() != null) {
            appointment.setDescription(input.description());
        }
        if (input.status() != null) {
            appointment.setStatus(input.status());
        }

        Appointment saved = appointmentRepository.save(appointment);
        publishEvent(saved, AppointmentEventType.UPDATED);
        return saved;
    }

    private void publishEvent(Appointment appointment, AppointmentEventType eventType) {
        Patient patient = appointment.getPatient();
        eventProducer.publish(new AppointmentChangedEvent(
                appointment.getId(),
                patient.getId(),
                patient.getEmail(),
                patient.getName(),
                appointment.getDateTime(),
                eventType));
    }

    /**
     * Enforces that a PACIENTE can only ever read their own appointments,
     * even if they alter the patientId in the request. MEDICO/ENFERMEIRO can
     * read any patient's history.
     */
    private void assertCanReadPatient(Long patientId) {
        AuthenticatedUser user = currentUser.get();
        boolean isPatient = user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PACIENTE"));

        if (isPatient && !patientId.equals(user.getPatientId())) {
            throw new AccessDeniedException("Paciente so pode acessar as proprias consultas");
        }
    }
}
