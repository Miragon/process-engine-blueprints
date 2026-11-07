package io.miragon.blueprint.adapter.outbound.dealer

import io.miragon.blueprint.application.port.outbound.BikeDealerPort
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.bike.OrderId
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Simulated bike dealer. Availability is decided by a small deny-list of bike ids so the
 * bike-unavailable → alternative branch can be triggered deterministically. The ids mirror the Bruno
 * scenario data: `BIKE-OOS` is the out-of-stock bike the `05-bike-unavailable` collection submits,
 * and `BIKE-CUSTOM` is the custom build whose order the dealer refuses to cancel, see
 * [requestCancellation].
 */
@Component
class BikeDealerAdapter : BikeDealerPort {

    private val log = KotlinLogging.logger {}

    private val outOfStockBikeIds = setOf("BIKE-OOS")

    /**
     * Demo mechanism: an order for one of these bikes cannot be cancelled, so withdrawing the
     * application takes the clarify-return branch of the cancel-bike-order process. Its order id
     * carries [NON_CANCELLABLE_ORDER_PREFIX], which keeps [requestCancellation] free of state.
     */
    private val nonCancellableBikeIds = setOf("BIKE-CUSTOM")

    override fun checkAvailability(bikeId: BikeId): Boolean {
        val available = bikeId.value !in outOfStockBikeIds
        return available
    }

    override fun order(bikeId: BikeId): OrderId {
        val prefix = if (bikeId.value in nonCancellableBikeIds) NON_CANCELLABLE_ORDER_PREFIX else ORDER_PREFIX
        val orderId = OrderId("$prefix${UUID.randomUUID()}")
        log.info { "Placed order ${orderId.value} for bike ${bikeId.value}" }
        return orderId
    }

    override fun requestCancellation(orderId: OrderId): Boolean {
        log.info { "Requesting cancellation of order ${orderId.value}" }
        return !orderId.value.startsWith(NON_CANCELLABLE_ORDER_PREFIX)
    }

    override fun bookCancellationCosts(orderId: OrderId) {
        // A real system would book the dealer's cancellation fee here.
        log.info { "Booking cancellation costs for order ${orderId.value}" }
    }

    companion object {
        private const val ORDER_PREFIX = "ORDER-"
        private const val NON_CANCELLABLE_ORDER_PREFIX = "ORDER-CUSTOM-"
    }
}
