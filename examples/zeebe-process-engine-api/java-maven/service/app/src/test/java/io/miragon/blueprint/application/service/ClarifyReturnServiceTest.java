package io.miragon.blueprint.application.service;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.LeasingProcess;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClarifyReturnServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final LeasingProcess process = mock(LeasingProcess.class);
    private final ClarifyReturnService underTest = new ClarifyReturnService(repository, process);

    @Test
    @DisplayName("clarifyReturn completes the return clarification of a known application")
    void clarifyReturnCompletesTheReturnClarificationOfAKnownApplication() {

        // given: a withdrawn application whose order the dealer could not cancel
        LeasingApplication application = testLeasingApplication().status(LeasingStatus.WITHDRAWN).build();
        when(repository.findById(application.id())).thenReturn(Optional.of(application));

        // when: the back office confirms the return
        underTest.clarifyReturn(application.id());

        // then: the return clarification is completed in the process
        verify(repository).findById(application.id());
        verify(process).completeReturnClarification(application.id());
        verifyNoMoreInteractions(repository, process);
    }

    @Test
    @DisplayName("clarifyReturn fails for an unknown application")
    void clarifyReturnFailsForAnUnknownApplication() {

        // given: an application id the repository does not know
        ApplicationId unknownId = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174999"));
        when(repository.findById(unknownId)).thenReturn(Optional.empty());

        // when / then: the lookup fails and the process is not touched
        assertThatThrownBy(() -> underTest.clarifyReturn(unknownId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unknown application " + unknownId);
        verify(repository).findById(unknownId);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(process);
    }
}
