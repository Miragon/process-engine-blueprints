package io.miragon.blueprint.application.port.inbound

import io.miragon.blueprint.domain.leasing.LeasingApplication

interface OrderBikeUseCase {
    fun orderBike(application: LeasingApplication): LeasingApplication
}
