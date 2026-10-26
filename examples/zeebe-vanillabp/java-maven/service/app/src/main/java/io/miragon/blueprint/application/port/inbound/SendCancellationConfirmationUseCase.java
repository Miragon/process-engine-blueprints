package io.miragon.blueprint.application.port.inbound;

import io.miragon.blueprint.domain.leasing.LeasingApplication;

/** Confirms to the customer that the application was cancelled. */
@FunctionalInterface
public interface SendCancellationConfirmationUseCase {

    LeasingApplication sendCancellationConfirmation(LeasingApplication application);
}
