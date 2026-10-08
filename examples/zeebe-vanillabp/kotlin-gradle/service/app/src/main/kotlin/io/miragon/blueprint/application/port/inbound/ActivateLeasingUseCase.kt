package io.miragon.blueprint.application.port.inbound

import io.miragon.blueprint.domain.leasing.LeasingApplication

interface ActivateLeasingUseCase {
    fun activate(application: LeasingApplication): LeasingApplication
}
