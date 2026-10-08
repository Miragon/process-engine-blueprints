package io.miragon.blueprint.application.port.inbound

import io.miragon.blueprint.domain.leasing.LeasingApplication

/** Compensation: revokes a contract that was already sent. */
fun interface CancelContractUseCase {
    fun cancelContract(application: LeasingApplication)
}
