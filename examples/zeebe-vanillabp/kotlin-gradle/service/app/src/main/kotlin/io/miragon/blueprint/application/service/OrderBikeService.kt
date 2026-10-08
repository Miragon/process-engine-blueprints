package io.miragon.blueprint.application.service

import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.miragon.blueprint.application.port.outbound.BikeDealerPort
import io.miragon.blueprint.domain.leasing.LeasingApplication
import org.springframework.stereotype.Service

@Service
class OrderBikeService(
    private val bikeDealer: BikeDealerPort,
) : OrderBikeUseCase {

    override fun orderBike(application: LeasingApplication): LeasingApplication {
        if (application.orderId != null) return application
        if (!bikeDealer.checkAvailability(application.bikeId)) return application
        return application.documentOrder(bikeDealer.order(application.bikeId))
    }
}
