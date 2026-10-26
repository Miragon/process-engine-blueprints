package io.miragon.blueprint.application.service;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.NotificationPort;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class SendSignatureReminderServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final NotificationPort notification = mock(NotificationPort.class);
    private final SendSignatureReminderService underTest = new SendSignatureReminderService(repository, notification);

    @Test
    @DisplayName("sendSignatureReminder loads the application and reminds the customer")
    void sendSignatureReminderLoadsTheApplicationAndRemindsTheCustomer() {

        // given: an application in the repository
        LeasingApplication application = testLeasingApplication();
        when(repository.findById(application.id())).thenReturn(Optional.of(application));

        // when: the signature reminder is sent
        underTest.sendSignatureReminder(application.id());

        // then: the application is loaded and the customer is reminded
        verify(repository).findById(application.id());
        verify(notification).send(any(), eq(application));
        verifyNoMoreInteractions(repository, notification);
    }
}
