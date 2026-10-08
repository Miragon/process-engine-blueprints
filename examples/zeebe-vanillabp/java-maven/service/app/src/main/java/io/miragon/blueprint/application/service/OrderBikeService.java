package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase;
import io.miragon.blueprint.application.port.outbound.BikeDealerPort;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.springframework.stereotype.Service;

@Service
public class OrderBikeService implements OrderBikeUseCase {

    private final BikeDealerPort bikeDealer;

    public OrderBikeService(BikeDealerPort bikeDealer) {
        this.bikeDealer = bikeDealer;
    }

    @Override
    public LeasingApplication orderBike(LeasingApplication application) {
        if (application.orderId() != null) {
            return application;
        }
        if (!bikeDealer.checkAvailability(application.bikeId())) {
            return application;
        }
        return application.documentOrder(bikeDealer.order(application.bikeId()));
    }
}
