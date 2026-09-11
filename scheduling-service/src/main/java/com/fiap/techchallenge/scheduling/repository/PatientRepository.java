package com.fiap.techchallenge.scheduling.repository;

import com.fiap.techchallenge.scheduling.domain.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRepository extends JpaRepository<Patient, Long> {
}
