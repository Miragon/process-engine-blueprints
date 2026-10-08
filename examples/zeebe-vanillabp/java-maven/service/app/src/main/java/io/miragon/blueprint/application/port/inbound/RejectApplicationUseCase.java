package io.miragon.blueprint.application.port.inbound;

import io.miragon.blueprint.domain.leasing.LeasingApplication;

public interface RejectApplicationUseCase {

    LeasingApplication reject(LeasingApplication application);
}
