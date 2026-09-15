package io.miragon.blueprint.application.service;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.NotificationPort;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SendCancellationConfirmationServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final NotificationPort notification = mock(NotificationPort.class);
    private final SendCancellationConfirmationService underTest =
            new SendCancellationConfirmationService(repository, notification);

    @Test
    void sendCancellationConfirmationLoadsTheApplicationAndConfirmsTheCancellation() {

        // given: an application in the repository
        LeasingApplication application = testLeasingApplication().build();
        when(repository.findById(application.id())).thenReturn(Optional.of(application));
        doNothing().when(notification).send(any(), eq(application));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when: the cancellation confirmation is sent
        underTest.sendCancellationConfirmation(application.id());

        // then: the application is loaded, the customer is informed, and the status is persisted
        verify(repository).findById(application.id());
        verify(notification).send(any(), eq(application));
        verify(repository).save(argThat(app -> app.status() == LeasingStatus.CANCELLED));
        verifyNoMoreInteractions(repository, notification);
    }
}
