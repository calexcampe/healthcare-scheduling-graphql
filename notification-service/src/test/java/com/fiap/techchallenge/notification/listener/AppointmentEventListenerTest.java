package com.fiap.techchallenge.notification.listener;

import com.fiap.techchallenge.notification.event.AppointmentChangedEvent;
import com.fiap.techchallenge.notification.event.AppointmentEventType;
import com.fiap.techchallenge.notification.service.NotificationSenderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AppointmentEventListenerTest {

    @Mock
    private NotificationSenderService notificationSenderService;

    @InjectMocks
    private AppointmentEventListener listener;

    @Test
    void processesEventByDelegatingToSenderService() {
        AppointmentChangedEvent event = new AppointmentChangedEvent(
                1L, 2L, "paciente@example.com", "Joao Silva", LocalDateTime.now().plusDays(1), AppointmentEventType.CREATED);

        listener.onAppointmentChanged(event);

        verify(notificationSenderService).sendReminder(event);
    }
}
