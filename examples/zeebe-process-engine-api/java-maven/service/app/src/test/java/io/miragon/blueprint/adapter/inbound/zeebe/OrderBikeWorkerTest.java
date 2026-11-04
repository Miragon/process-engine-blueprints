package io.miragon.blueprint.adapter.inbound.zeebe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred;
import io.miragon.blueprint.adapter.outbound.dealer.BikeDealerAdapter.DealerUnavailableException;
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.BikeUnavailableException;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderBikeWorkerTest {

    private final OrderBikeUseCase useCase = mock(OrderBikeUseCase.class);

    private final OrderBikeWorker worker = new OrderBikeWorker(useCase);

    @Test
    @DisplayName("returns the order id as output variable")
    void returnsTheOrderIdAsOutputVariable() throws BpmnErrorOccurred {
        // given
        UUID id = UUID.randomUUID();
        when(useCase.orderBike(ApplicationId.of(id.toString()), new BikeId("BIKE-900"))).thenReturn(new OrderId("ORDER-1"));

        // when
        Map<String, Object> variables = worker.handle(id.toString(), "BIKE-900");

        // then
        assertThat(variables).isEqualTo(Map.of("orderId", "ORDER-1"));
    }

    @Test
    @DisplayName("throws the bikeUnavailable BPMN error when the dealer cannot deliver the bike")
    void throwsTheBikeUnavailableBpmnErrorWhenTheDealerCannotDeliverTheBike() {
        // given
        UUID id = UUID.randomUUID();
        when(useCase.orderBike(any(), any())).thenThrow(new BikeUnavailableException(new BikeId("BIKE-OOS")));

        // when / then
        assertThatThrownBy(() -> worker.handle(id.toString(), "BIKE-OOS"))
            .isInstanceOfSatisfying(BpmnErrorOccurred.class, error -> {
                assertThat(error.getErrorCode()).isEqualTo("bikeUnavailable");
                assertThat(error.getMessage()).isEqualTo("Bike BIKE-OOS is not available at the dealer");
            });
    }

    @Test
    @DisplayName("lets the dealer outage through so the worker library fails the job and retries count down to an incident")
    void letsTheDealerOutageThroughSoTheWorkerLibraryFailsTheJob() {
        // given: the simulated dealer outage (BIKE-FAIL) propagates out of the use case
        UUID id = UUID.randomUUID();
        when(useCase.orderBike(any(), any())).thenThrow(new DealerUnavailableException(new BikeId("BIKE-FAIL")));

        // when / then
        assertThatThrownBy(() -> worker.handle(id.toString(), "BIKE-FAIL"))
            .isInstanceOf(DealerUnavailableException.class);
    }
}
