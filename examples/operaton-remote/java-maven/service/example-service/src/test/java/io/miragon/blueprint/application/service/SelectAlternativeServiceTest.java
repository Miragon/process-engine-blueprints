package io.miragon.blueprint.application.service;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.application.port.inbound.SelectAlternativeUseCase;
import io.miragon.blueprint.application.port.outbound.BikePortfolioRepository;
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.LeasingProcess;
import io.miragon.blueprint.domain.bike.Bike;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SelectAlternativeServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final BikePortfolioRepository bikePortfolio = mock(BikePortfolioRepository.class);
    private final LeasingProcess process = mock(LeasingProcess.class);
    private final SelectAlternativeService underTest =
        new SelectAlternativeService(repository, bikePortfolio, process);

    @Test
    @DisplayName("an accepted alternative registers the new bike and hands it to the process")
    void anAcceptedAlternativeRegistersTheNewBikeAndHandsItToTheProcess() {

        // given: an application whose requested bike was unavailable
        LeasingApplication application = testLeasingApplication().build();
        when(repository.findById(application.id())).thenReturn(Optional.of(application));
        when(bikePortfolio.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when: an alternative bike is selected
        underTest.selectAlternative(
            new SelectAlternativeUseCase.Command(application.id(), true, new BikeId("BIKE-ALT"), "Aero Road 700"));

        // then: the alternative is registered in the portfolio and the task is completed with it; the order step stores it
        verify(repository).findById(application.id());
        verify(bikePortfolio).save(new Bike(new BikeId("BIKE-ALT"), "Aero Road 700"));
        verify(process).completeAlternativeClarification(application.id(), true, new BikeId("BIKE-ALT"));
        verifyNoMoreInteractions(repository, bikePortfolio, process);
    }

    @Test
    @DisplayName("no alternative completes the user task without touching the bike")
    void noAlternativeCompletesTheUserTaskWithoutTouchingTheBike() {

        // given: an application whose requested bike was unavailable
        LeasingApplication application = testLeasingApplication().build();
        when(repository.findById(application.id())).thenReturn(Optional.of(application));

        // when: no alternative is found
        underTest.selectAlternative(new SelectAlternativeUseCase.Command(application.id(), false, null, null));

        // then: neither the portfolio nor the application is touched, and the task is completed as declined
        verify(repository).findById(application.id());
        verify(process).completeAlternativeClarification(application.id(), false, null);
        verifyNoMoreInteractions(repository, bikePortfolio, process);
    }

    @Test
    @DisplayName("selectAlternative fails for an unknown application")
    void selectAlternativeFailsForAnUnknownApplication() {

        // given: a decision for an application the repository does not know
        ApplicationId unknownId = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174999"));
        when(repository.findById(unknownId)).thenReturn(Optional.empty());

        // when / then: the lookup fails before the portfolio, the application or the task is touched
        assertThatThrownBy(() -> underTest.selectAlternative(
                new SelectAlternativeUseCase.Command(unknownId, true, new BikeId("BIKE-ALT"), "Aero Road 700")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unknown application " + unknownId);

        verify(repository).findById(unknownId);
        verify(repository, never()).save(any());
        verify(process, never()).completeAlternativeClarification(any(), anyBoolean(), any());
        verifyNoInteractions(bikePortfolio);
        verifyNoMoreInteractions(repository, process);
    }
}
