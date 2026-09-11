package com.fiap.techchallenge.scheduling.graphql;

import com.fiap.techchallenge.scheduling.domain.Appointment;
import com.fiap.techchallenge.scheduling.graphql.dto.AppointmentInput;
import com.fiap.techchallenge.scheduling.service.AppointmentService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class AppointmentGraphQLController {

    private final AppointmentService appointmentService;

    public AppointmentGraphQLController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @QueryMapping
    public List<Appointment> patientHistory(@Argument Long patientId) {
        return appointmentService.getPatientHistory(patientId);
    }

    @QueryMapping
    public List<Appointment> futureAppointments(@Argument Long patientId) {
        return appointmentService.getFutureAppointments(patientId);
    }

    @MutationMapping
    public Appointment createAppointment(@Argument AppointmentInput input) {
        return appointmentService.createAppointment(input);
    }

    @MutationMapping
    public Appointment updateAppointment(@Argument Long id, @Argument AppointmentInput input) {
        return appointmentService.updateAppointment(id, input);
    }

    @SchemaMapping(typeName = "Appointment", field = "dateTime")
    public String dateTime(Appointment appointment) {
        return appointment.getDateTime() != null ? appointment.getDateTime().toString() : null;
    }
}
