package io.miragon.blueprint.application.port.inbound

import io.miragon.blueprint.domain.leasing.LeasingApplication

/** Confirms to the customer that the application was cancelled. */
fun interface SendCancellationConfirmationUseCase {
    fun sendCancellationConfirmation(application: LeasingApplication): LeasingApplication
}
