package io.miragon.blueprint.application.port.inbound

import io.miragon.blueprint.domain.leasing.LeasingApplication

/** Reminds the customer that the contract is still awaiting signature. */
fun interface SendSignatureReminderUseCase {
    fun sendSignatureReminder(application: LeasingApplication)
}
