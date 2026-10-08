package io.miragon.blueprint.adapter.inbound.workflow;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.adapter.outbound.dealer.BikeDealerAdapter.DealerUnavailableException;
import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import org.junit.jupiter.api.Test;

class OrderBikeTaskTest {

    private final OrderBikeUseCase useCase = mock(OrderBikeUseCase.class);
    private final OrderBikeTask task = new OrderBikeTask(useCase);

    @Test
    void adoptsThePlacedOrderSoTheEngineSeesAnAvailableBike() {
        // given
        LeasingApplication application = testLeasingApplication().build();
        LeasingApplicationAggregate aggregate = LeasingApplicationAggregate.of(application);
        when(useCase.orderBike(application)).thenReturn(application.documentOrder(new OrderId("ORDER-1")));

        // when
        task.orderBike(aggregate);

        // then
        assertThat(aggregate.getOrderId()).isEqualTo("ORDER-1");
        assertThat(aggregate.getStatus()).isEqualTo(LeasingStatus.ORDERED);
        assertThat(aggregate.isBikeAvailable()).isTrue();
    }

    @Test
    void reportsAnUnavailableBikeWhenNoOrderWasPlaced() {
        // given
        LeasingApplication application = testLeasingApplication().build();
        LeasingApplicationAggregate aggregate = LeasingApplicationAggregate.of(application);
        when(useCase.orderBike(application)).thenReturn(application);

        // when
        task.orderBike(aggregate);

        // then
        assertThat(aggregate.getOrderId()).isNull();
        assertThat(aggregate.isBikeAvailable()).isFalse();
    }

    @Test
    void letsTheDealerOutageEscapeSoVanillaBpFailsTheJobAndItsRetriesCountDownToAnIncident() {
        // given: the simulated dealer outage (BIKE-FAIL) propagates out of the use case
        LeasingApplicationAggregate aggregate =
                LeasingApplicationAggregate.of(testLeasingApplication().bikeId(new BikeId("BIKE-FAIL")).build());
        when(useCase.orderBike(any())).thenThrow(new DealerUnavailableException(new BikeId("BIKE-FAIL")));

        // when / then: the outage is not swallowed and the workflow aggregate stays unordered
        assertThatThrownBy(() -> task.orderBike(aggregate)).isInstanceOf(DealerUnavailableException.class);
        assertThat(aggregate.getOrderId()).isNull();
        assertThat(aggregate.getStatus()).isEqualTo(LeasingStatus.RECEIVED);
    }
}
