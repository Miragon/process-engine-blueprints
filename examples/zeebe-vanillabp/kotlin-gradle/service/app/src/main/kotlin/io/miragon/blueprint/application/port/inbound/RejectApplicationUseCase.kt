package io.miragon.blueprint.application.port.inbound

import io.miragon.blueprint.domain.leasing.LeasingApplication

interface RejectApplicationUseCase {
    fun reject(application: LeasingApplication): LeasingApplication
}
