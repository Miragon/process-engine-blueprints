package io.miragon.blueprint.application.port.inbound;

import io.miragon.blueprint.domain.leasing.LeasingApplication;

/** Sends the leasing contract to the customer for signature. */
@FunctionalInterface
public interface SendContractUseCase {

    LeasingApplication sendContract(LeasingApplication application);
}
