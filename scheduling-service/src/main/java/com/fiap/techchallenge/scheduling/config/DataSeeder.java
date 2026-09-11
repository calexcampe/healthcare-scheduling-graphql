package com.fiap.techchallenge.scheduling.config;

import com.fiap.techchallenge.scheduling.domain.Appointment;
import com.fiap.techchallenge.scheduling.domain.AppointmentStatus;
import com.fiap.techchallenge.scheduling.domain.Patient;
import com.fiap.techchallenge.scheduling.domain.Role;
import com.fiap.techchallenge.scheduling.domain.User;
import com.fiap.techchallenge.scheduling.repository.AppointmentRepository;
import com.fiap.techchallenge.scheduling.repository.PatientRepository;
import com.fiap.techchallenge.scheduling.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Seeds test users/patients/appointments for local dev and for the Postman
 * collection examples in the README. Runs only against the in-memory H2 DB.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository,
                       PatientRepository patientRepository,
                       AppointmentRepository appointmentRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Patient joao = patientRepository.save(new Patient(null, "Joao Silva", "joao.silva@example.com"));
        Patient maria = patientRepository.save(new Patient(null, "Maria Souza", "maria.souza@example.com"));

        userRepository.save(new User(null, "medico1", passwordEncoder.encode("senha123"), Role.MEDICO, null));
        userRepository.save(new User(null, "enfermeiro1", passwordEncoder.encode("senha123"), Role.ENFERMEIRO, null));
        userRepository.save(new User(null, "paciente1", passwordEncoder.encode("senha123"), Role.PACIENTE, joao));
        userRepository.save(new User(null, "paciente2", passwordEncoder.encode("senha123"), Role.PACIENTE, maria));

        appointmentRepository.save(new Appointment(null, joao, "Dr. Carlos Lima",
                LocalDateTime.now().minusDays(10), "Consulta de rotina", AppointmentStatus.REALIZADA));
        appointmentRepository.save(new Appointment(null, joao, "Dr. Carlos Lima",
                LocalDateTime.now().plusDays(5), "Retorno", AppointmentStatus.AGENDADA));
        appointmentRepository.save(new Appointment(null, maria, "Dra. Ana Paula",
                LocalDateTime.now().plusDays(2), "Primeira consulta", AppointmentStatus.AGENDADA));
    }
}
