package com.fiap.techchallenge.scheduling.repository;

import com.fiap.techchallenge.scheduling.domain.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByPatientId(Long patientId);

    List<Appointment> findByPatientIdAndDateTimeAfter(Long patientId, LocalDateTime after);
}
