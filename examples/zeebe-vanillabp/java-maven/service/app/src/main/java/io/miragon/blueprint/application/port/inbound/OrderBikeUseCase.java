package io.miragon.blueprint.application.port.inbound;

import io.miragon.blueprint.domain.leasing.LeasingApplication;

public interface OrderBikeUseCase {

    LeasingApplication orderBike(LeasingApplication application);
}
