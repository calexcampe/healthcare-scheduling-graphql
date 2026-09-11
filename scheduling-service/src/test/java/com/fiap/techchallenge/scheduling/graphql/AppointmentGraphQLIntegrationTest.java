package com.fiap.techchallenge.scheduling.graphql;

import com.fiap.techchallenge.scheduling.domain.Patient;
import com.fiap.techchallenge.scheduling.domain.Role;
import com.fiap.techchallenge.scheduling.domain.User;
import com.fiap.techchallenge.scheduling.messaging.AppointmentEventProducer;
import com.fiap.techchallenge.scheduling.repository.AppointmentRepository;
import com.fiap.techchallenge.scheduling.repository.PatientRepository;
import com.fiap.techchallenge.scheduling.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentGraphQLIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private AppointmentEventProducer eventProducer;

    private Patient patientA;
    private Patient patientB;

    @BeforeEach
    void setUp() {
        // Unique usernames per test run (H2 in-memory DB persists across tests in the same context).
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        patientA = patientRepository.save(new Patient(null, "Paciente A", "a@example.com"));
        patientB = patientRepository.save(new Patient(null, "Paciente B", "b@example.com"));

        userRepository.save(new User(null, "medico-" + suffix, passwordEncoder.encode("senha"), Role.MEDICO, null));
        userRepository.save(new User(null, "enfermeiro-" + suffix, passwordEncoder.encode("senha"), Role.ENFERMEIRO, null));
        userRepository.save(new User(null, "pacienteA-" + suffix, passwordEncoder.encode("senha"), Role.PACIENTE, patientA));

        this.medicoUser = "medico-" + suffix;
        this.enfermeiroUser = "enfermeiro-" + suffix;
        this.pacienteAUser = "pacienteA-" + suffix;
    }

    private String medicoUser;
    private String enfermeiroUser;
    private String pacienteAUser;

    private GraphQlTester testerFor(String username) {
        WebTestClient.Builder builder = MockMvcWebTestClient.bindTo(mockMvc);
        return HttpGraphQlTester.builder(builder)
                .url("/graphql")
                .header(HttpHeaders.AUTHORIZATION, basicAuth(username, "senha"))
                .build();
    }

    private String basicAuth(String username, String password) {
        String raw = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes());
    }

    @Test
    void patientCanReadOwnHistory() {
        appointmentRepository.save(new com.fiap.techchallenge.scheduling.domain.Appointment(
                null, patientA, "Dr. X", LocalDateTime.now().minusDays(1), "desc",
                com.fiap.techchallenge.scheduling.domain.AppointmentStatus.REALIZADA));

        testerFor(pacienteAUser)
                .document("query($id: ID!) { patientHistory(patientId: $id) { id professionalName } }")
                .variable("id", patientA.getId())
                .execute()
                .path("patientHistory")
                .entityList(Object.class)
                .hasSizeGreaterThan(0);
    }

    @Test
    void patientCannotReadAnotherPatientHistory() {
        testerFor(pacienteAUser)
                .document("query($id: ID!) { patientHistory(patientId: $id) { id } }")
                .variable("id", patientB.getId())
                .execute()
                .errors()
                .expect(error -> error.getErrorType() == ErrorType.FORBIDDEN);
    }

    @Test
    void enfermeiroCanCreateAppointment() {
        testerFor(enfermeiroUser)
                .document("""
                        mutation($input: AppointmentInput!) {
                            createAppointment(input: $input) { id professionalName }
                        }
                        """)
                .variable("input", Map.of(
                        "patientId", patientA.getId(),
                        "professionalName", "Dr. Novo",
                        "dateTime", "2026-11-01T10:00:00",
                        "description", "Consulta"))
                .execute()
                .path("createAppointment.professionalName")
                .entity(String.class)
                .isEqualTo("Dr. Novo");
    }

    @Test
    void medicoCanCreateAppointment() {
        testerFor(medicoUser)
                .document("""
                        mutation($input: AppointmentInput!) {
                            createAppointment(input: $input) { id professionalName }
                        }
                        """)
                .variable("input", Map.of(
                        "patientId", patientA.getId(),
                        "professionalName", "Dr. Novo",
                        "dateTime", "2026-11-01T10:00:00"))
                .execute()
                .path("createAppointment.professionalName")
                .entity(String.class)
                .isEqualTo("Dr. Novo");
    }

    @Test
    void patientCannotCreateAppointment() {
        GraphQlTester.Response response = testerFor(pacienteAUser)
                .document("""
                        mutation($input: AppointmentInput!) {
                            createAppointment(input: $input) { id }
                        }
                        """)
                .variable("input", Map.of(
                        "patientId", patientA.getId(),
                        "professionalName", "Dr. Novo",
                        "dateTime", "2026-11-01T10:00:00"))
                .execute();

        response.errors().satisfy(errors -> assertThat(errors).isNotEmpty());
    }

    @Test
    void medicoCanUpdateAppointment() {
        var appointment = appointmentRepository.save(new com.fiap.techchallenge.scheduling.domain.Appointment(
                null, patientA, "Dr. X", LocalDateTime.now().plusDays(1), "desc",
                com.fiap.techchallenge.scheduling.domain.AppointmentStatus.AGENDADA));

        testerFor(medicoUser)
                .document("""
                        mutation($id: ID!, $input: AppointmentInput!) {
                            updateAppointment(id: $id, input: $input) { id status }
                        }
                        """)
                .variable("id", appointment.getId())
                .variable("input", Map.of(
                        "patientId", patientA.getId(),
                        "status", "REALIZADA"))
                .execute()
                .path("updateAppointment.status")
                .entity(String.class)
                .isEqualTo("REALIZADA");
    }

    @Test
    void enfermeiroCanUpdateAppointment() {
        var appointment = appointmentRepository.save(new com.fiap.techchallenge.scheduling.domain.Appointment(
                null, patientA, "Dr. X", LocalDateTime.now().plusDays(1), "desc",
                com.fiap.techchallenge.scheduling.domain.AppointmentStatus.AGENDADA));

        testerFor(enfermeiroUser)
                .document("""
                        mutation($id: ID!, $input: AppointmentInput!) {
                            updateAppointment(id: $id, input: $input) { id status }
                        }
                        """)
                .variable("id", appointment.getId())
                .variable("input", Map.of(
                        "patientId", patientA.getId(),
                        "status", "REALIZADA"))
                .execute()
                .path("updateAppointment.status")
                .entity(String.class)
                .isEqualTo("REALIZADA");
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/graphql")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"{ patientHistory(patientId: 1) { id } }\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
    }
}
