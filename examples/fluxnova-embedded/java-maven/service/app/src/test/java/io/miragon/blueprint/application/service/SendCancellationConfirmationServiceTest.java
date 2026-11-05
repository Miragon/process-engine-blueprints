package io.miragon.blueprint.application.service;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.NotificationPort;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SendCancellationConfirmationServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final NotificationPort notification = mock(NotificationPort.class);
    private final SendCancellationConfirmationService underTest =
            new SendCancellationConfirmationService(repository, notification);

    @Test
    void sendCancellationConfirmationConfirmsToTheCustomerAndMarksTheApplicationCancelled() {

        // given: an application in the repository
        LeasingApplication application = testLeasingApplication().build();
        when(repository.findById(application.id())).thenReturn(Optional.of(application));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when: the cancellation confirmation is sent
        underTest.sendCancellationConfirmation(application.id());

        // then: the customer is informed and the application is moved to CANCELLED
        verify(repository).findById(application.id());
        verify(notification).send(any(), eq(application));
        ArgumentCaptor<LeasingApplication> saved = ArgumentCaptor.forClass(LeasingApplication.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(LeasingStatus.CANCELLED);
        verifyNoMoreInteractions(repository, notification);
    }

    @Test
    void sendCancellationConfirmationFailsForAnUnknownApplication() {

        // given: an application id the repository does not know
        ApplicationId unknownId = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174999"));
        when(repository.findById(unknownId)).thenReturn(Optional.empty());

        // when / then: the lookup fails, nobody is notified and nothing is persisted
        assertThatThrownBy(() -> underTest.sendCancellationConfirmation(unknownId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unknown application " + unknownId);

        verify(repository).findById(unknownId);
        verify(repository, never()).save(any());
        verifyNoInteractions(notification);
        verifyNoMoreInteractions(repository);
    }
}
