package io.miragon.blueprint.application.service;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.application.port.outbound.BikeDealerPort;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.BikeUnavailableException;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import org.junit.jupiter.api.Test;

class OrderBikeServiceTest {

    private final BikeDealerPort bikeDealer = mock(BikeDealerPort.class);
    private final OrderBikeService underTest = new OrderBikeService(bikeDealer);

    @Test
    void orderBikePlacesAnOrderWhenTheDealerHasTheBikeInStock() {

        // given: an application whose bike is available at the dealer
        LeasingApplication application = testLeasingApplication().bikeId(new BikeId("BIKE-900")).build();
        when(bikeDealer.checkAvailability(application.bikeId())).thenReturn(true);
        when(bikeDealer.order(application.bikeId())).thenReturn(new OrderId("ORDER-900"));

        // when: the bike is ordered
        LeasingApplication ordered = underTest.orderBike(application);

        // then: the order id is recorded and the application moves to ORDERED
        assertThat(ordered)
                .isEqualTo(testLeasingApplication()
                        .bikeId(new BikeId("BIKE-900"))
                        .orderId(new OrderId("ORDER-900"))
                        .status(LeasingStatus.ORDERED)
                        .build());
        verify(bikeDealer).checkAvailability(application.bikeId());
        verify(bikeDealer).order(application.bikeId());
        verifyNoMoreInteractions(bikeDealer);
    }

    @Test
    void orderBikeReportsAnOutOfStockBikeAsUnavailable() {

        // given: an application whose bike is out of stock at the dealer
        LeasingApplication application = testLeasingApplication().bikeId(new BikeId("BIKE-OOS")).build();
        when(bikeDealer.checkAvailability(application.bikeId())).thenReturn(false);

        // when / then: ordering reports the bike as unavailable and places no order
        assertThatThrownBy(() -> underTest.orderBike(application))
                .isInstanceOf(BikeUnavailableException.class)
                .hasMessage("Bike BIKE-OOS is not available at the dealer");
        verify(bikeDealer).checkAvailability(application.bikeId());
        verifyNoMoreInteractions(bikeDealer);
    }

    @Test
    void orderBikeDoesNotOrderASecondBikeWhenTheTaskIsDeliveredAgain() {

        // given: an application that already carries the order of an earlier delivery
        LeasingApplication application =
                testLeasingApplication().orderId(new OrderId("ORDER-900")).status(LeasingStatus.ORDERED).build();

        // when: the bike is ordered again
        LeasingApplication ordered = underTest.orderBike(application);

        // then: the application is untouched and the dealer is not called
        assertThat(ordered).isEqualTo(application);
        verifyNoInteractions(bikeDealer);
    }
}
