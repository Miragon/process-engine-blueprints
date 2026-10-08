package io.miragon.blueprint.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import io.miragon.blueprint.domain.leasing.TestObjectBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

class ActivateLeasingServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final ActivateLeasingService underTest = new ActivateLeasingService(repository);

    @Test
    @DisplayName("activate loads the application, activates it, and persists the ACTIVE status")
    void activateLoadsTheApplicationActivatesItAndPersistsTheActiveStatus() {

        // given: a handed-over application
        ApplicationId id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        LeasingApplication application = TestObjectBuilder.aLeasingApplication()
                .id(id)
                .status(LeasingStatus.HANDED_OVER)
                .build();
        when(repository.findById(id)).thenReturn(Optional.of(application));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when: the leasing is activated
        underTest.activate(id);

        // then: the application is persisted with ACTIVE
        verify(repository).findById(id);
        verify(repository).save(argThat(saved -> saved.status() == LeasingStatus.ACTIVE));
        verifyNoMoreInteractions(repository);
    }
}
