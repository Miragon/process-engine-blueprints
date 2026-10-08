package io.miragon.blueprint.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.application.port.outbound.BikeDealerPort;
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.BikeUnavailableException;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import io.miragon.blueprint.domain.leasing.TestObjectBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class OrderBikeServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final BikeDealerPort bikeDealer = mock(BikeDealerPort.class);
    private final OrderBikeService underTest = new OrderBikeService(repository, bikeDealer);

    @Test
    @DisplayName("orderBike places an order when the dealer has the bike in stock")
    void orderBikePlacesAnOrderWhenTheDealerHasTheBikeInStock() {

        // given: an application whose bike is available at the dealer
        LeasingApplication application = TestObjectBuilder.aLeasingApplication()
                .bikeId(new BikeId("BIKE-900"))
                .build();
        when(repository.findById(application.id())).thenReturn(Optional.of(application));
        when(bikeDealer.checkAvailability(application.bikeId())).thenReturn(true);
        when(bikeDealer.order(application.bikeId())).thenReturn(new OrderId("ORDER-900"));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when: the bike is ordered
        OrderId orderId = underTest.orderBike(application.id());

        // then: the order id is returned and the application moves to ORDERED
        assertThat(orderId).isEqualTo(new OrderId("ORDER-900"));
        verify(bikeDealer).checkAvailability(application.bikeId());
        verify(bikeDealer).order(application.bikeId());
        verify(repository).save(argThat(saved ->
                saved.status() == LeasingStatus.ORDERED && new OrderId("ORDER-900").equals(saved.orderId())));
        verifyNoMoreInteractions(bikeDealer);
    }

    @Test
    @DisplayName("orderBike reports an out-of-stock bike as unavailable and places no order")
    void orderBikeReportsAnOutOfStockBikeAsUnavailableAndPlacesNoOrder() {

        // given: an application whose bike is out of stock at the dealer
        LeasingApplication application = TestObjectBuilder.aLeasingApplication()
                .bikeId(new BikeId("BIKE-OOS"))
                .build();
        when(repository.findById(application.id())).thenReturn(Optional.of(application));
        when(bikeDealer.checkAvailability(application.bikeId())).thenReturn(false);

        // when / then: ordering reports the bike as unavailable and places no order
        assertThatThrownBy(() -> underTest.orderBike(application.id()))
                .isInstanceOf(BikeUnavailableException.class)
                .hasMessage("Bike BIKE-OOS is not available at the dealer");
        verify(bikeDealer).checkAvailability(application.bikeId());
        verify(bikeDealer, never()).order(any());
        verify(repository, never()).save(any());
        verifyNoMoreInteractions(bikeDealer);
    }
}
