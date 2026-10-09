package io.miragon.blueprint.application.service;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SelectAlternativeServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final BikePortfolioRepository bikePortfolio = mock(BikePortfolioRepository.class);
    private final LeasingProcess process = mock(LeasingProcess.class);
    private final SelectAlternativeService underTest =
            new SelectAlternativeService(repository, bikePortfolio, process);

    @Test
    void anAcceptedAlternativeRegistersTheNewBikeAndHandsItToTheProcess() {

        // given: an application whose requested bike was unavailable
        LeasingApplication application = testLeasingApplication().build();
        when(repository.findById(application.id())).thenReturn(Optional.of(application));
        when(bikePortfolio.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(process).completeAlternativeClarification(any(), anyBoolean(), any());

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
    void noAlternativeCompletesTheUserTaskWithoutTouchingTheBike() {

        // given: an application whose requested bike was unavailable
        LeasingApplication application = testLeasingApplication().build();
        when(repository.findById(application.id())).thenReturn(Optional.of(application));
        doNothing().when(process).completeAlternativeClarification(any(), anyBoolean(), any());

        // when: no alternative is found
        underTest.selectAlternative(new SelectAlternativeUseCase.Command(application.id(), false, null, null));

        // then: neither the portfolio nor the application is touched, and the task is completed as declined
        verify(repository).findById(application.id());
        verify(process).completeAlternativeClarification(application.id(), false, null);
        verifyNoMoreInteractions(repository, bikePortfolio, process);
    }

    @Test
    void anUnknownApplicationIsReportedByItsPlainId() {

        // given: no application is stored under the id
        ApplicationId id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        when(repository.findById(id)).thenReturn(Optional.empty());

        // when / then: the decision is refused, naming the id as the client sent it
        assertThatThrownBy(() -> underTest.selectAlternative(new SelectAlternativeUseCase.Command(id, false, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unknown application 123e4567-e89b-12d3-a456-426614174000");
        verify(repository).findById(id);
        verifyNoMoreInteractions(repository, bikePortfolio, process);
    }
}
