package io.miragon.blueprint.application.service;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class SelectAlternativeServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final BikePortfolioRepository bikePortfolio = mock(BikePortfolioRepository.class);
    private final LeasingProcess process = mock(LeasingProcess.class);
    private final SelectAlternativeService underTest =
            new SelectAlternativeService(repository, bikePortfolio, process);

    @Test
    @DisplayName("an accepted alternative registers the new bike, points the application at it and completes the task")
    void anAcceptedAlternativeRegistersTheNewBikePointsTheApplicationAtItAndCompletesTheTask() {

        // given: an application whose requested bike was unavailable
        LeasingApplication application = testLeasingApplication();
        when(repository.findById(application.id())).thenReturn(Optional.of(application));
        when(bikePortfolio.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when: an alternative bike is selected
        underTest.selectAlternative(
                new SelectAlternativeUseCase.Command(application.id(), true, new BikeId("BIKE-ALT"), "Aero Road 700")
        );

        // then: the alternative is registered in the portfolio, the application points at it and the task is completed
        verify(repository).findById(application.id());
        verify(bikePortfolio).save(new Bike(new BikeId("BIKE-ALT"), "Aero Road 700"));
        verify(repository).save(argThat(it -> new BikeId("BIKE-ALT").equals(it.bikeId())));
        verify(process).completeAlternativeClarification(application.id(), true, new BikeId("BIKE-ALT"));
        verifyNoMoreInteractions(repository, bikePortfolio, process);
    }

    @Test
    @DisplayName("no alternative completes the user task without touching the bike")
    void noAlternativeCompletesTheUserTaskWithoutTouchingTheBike() {

        // given: an application whose requested bike was unavailable
        LeasingApplication application = testLeasingApplication();
        when(repository.findById(application.id())).thenReturn(Optional.of(application));

        // when: no alternative is found
        underTest.selectAlternative(new SelectAlternativeUseCase.Command(application.id(), false));

        // then: neither the portfolio nor the application is touched, and the task is completed as declined
        verify(repository).findById(application.id());
        verify(process).completeAlternativeClarification(application.id(), false, null);
        verifyNoMoreInteractions(repository, bikePortfolio, process);
    }

    @Test
    @DisplayName("an unknown application is reported with its id and nothing else happens")
    void anUnknownApplicationIsReportedWithItsIdAndNothingElseHappens() {

        // given: no application exists for the id
        ApplicationId unknown = ApplicationId.of("123e4567-e89b-12d3-a456-426614174000");
        when(repository.findById(unknown)).thenReturn(Optional.empty());

        // when/then: selecting an alternative names the missing application
        assertThatThrownBy(() -> underTest.selectAlternative(new SelectAlternativeUseCase.Command(unknown, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unknown application ApplicationId(value=123e4567-e89b-12d3-a456-426614174000)");
        verify(repository).findById(unknown);
        verifyNoMoreInteractions(repository, bikePortfolio, process);
    }
}
