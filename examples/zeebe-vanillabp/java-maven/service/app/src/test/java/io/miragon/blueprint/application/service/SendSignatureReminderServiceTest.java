package io.miragon.blueprint.application.service;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.miragon.blueprint.application.port.outbound.NotificationPort;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.junit.jupiter.api.Test;

class SendSignatureReminderServiceTest {

    private final NotificationPort notification = mock(NotificationPort.class);
    private final SendSignatureReminderService underTest = new SendSignatureReminderService(notification);

    @Test
    void sendSignatureReminderRemindsTheCustomer() {

        // given: an application awaiting signature
        LeasingApplication application = testLeasingApplication().build();

        // when: the signature reminder is sent
        underTest.sendSignatureReminder(application);

        // then: the customer is reminded
        verify(notification).send("Reminder: your leasing contract is still awaiting signature", application);
        verifyNoMoreInteractions(notification);
    }
}
