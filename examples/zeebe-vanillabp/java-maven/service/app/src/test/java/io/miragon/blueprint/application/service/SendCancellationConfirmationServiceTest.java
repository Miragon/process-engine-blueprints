package io.miragon.blueprint.application.service;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.miragon.blueprint.application.port.outbound.NotificationPort;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import org.junit.jupiter.api.Test;

class SendCancellationConfirmationServiceTest {

    private final NotificationPort notification = mock(NotificationPort.class);
    private final SendCancellationConfirmationService underTest =
            new SendCancellationConfirmationService(notification);

    @Test
    void sendCancellationConfirmationNotifiesTheCustomerAndReturnsTheApplicationAsCancelled() {

        // given: a withdrawn application
        LeasingApplication application = testLeasingApplication().status(LeasingStatus.WITHDRAWN).build();

        // when: the cancellation confirmation is sent
        LeasingApplication cancelled = underTest.sendCancellationConfirmation(application);

        // then: the customer is informed and only the status moved to CANCELLED
        assertThat(cancelled).isEqualTo(testLeasingApplication().status(LeasingStatus.CANCELLED).build());
        verify(notification).send("Your bike-leasing application has been cancelled", application);
        verifyNoMoreInteractions(notification);
    }
}
