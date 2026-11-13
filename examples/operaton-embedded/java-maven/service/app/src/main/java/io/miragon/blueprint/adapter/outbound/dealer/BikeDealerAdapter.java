package io.miragon.blueprint.adapter.outbound.dealer;

import io.miragon.blueprint.application.port.outbound.BikeDealerPort;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.OrderId;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Simulated bike dealer. Availability is decided by a small deny-list of bike ids so the
 * bike-unavailable → alternative branch can be triggered deterministically. The ids mirror the Bruno
 * scenario data: {@code BIKE-OOS} is the out-of-stock bike the {@code 05-bike-unavailable} collection
 * submits, {@code BIKE-FAIL} is the poison id the {@code 06-incident-demo} collection uses to make
 * the dealer "outage" throw — see {@link #order} — and {@code BIKE-CUSTOM} is the custom build whose
 * order the dealer refuses to cancel, see {@link #requestCancellation}.
 */
@Component
public class BikeDealerAdapter implements BikeDealerPort {

    private static final Logger log = LoggerFactory.getLogger(BikeDealerAdapter.class);

    private static final String ORDER_PREFIX = "ORDER-";

    private static final String NON_CANCELLABLE_ORDER_PREFIX = "ORDER-CUSTOM-";

    private final Set<String> outOfStockBikeIds = Set.of("BIKE-OOS");

    /**
     * Demo mechanism (not a real integration fault): ordering one of these bikes makes the dealer
     * call fail as if the dealer's API were unreachable, which drives the reproducible outage demo. How
     * the failed order step is retried is up to the process engine and its inbound adapter. It stays
     * available in {@link #checkAvailability} so the flow reaches the order step (rather than the out-of-stock branch)
     * before failing.
     */
    private final Set<String> unavailableDealerBikeIds = Set.of("BIKE-FAIL");

    /**
     * Demo mechanism: an order for one of these bikes cannot be cancelled, so withdrawing the
     * application takes the clarify-return branch of the cancel-bike-order process. Its order id
     * carries {@link #NON_CANCELLABLE_ORDER_PREFIX}, which keeps {@link #requestCancellation} free of state.
     */
    private final Set<String> nonCancellableBikeIds = Set.of("BIKE-CUSTOM");

    @Override
    public boolean checkAvailability(BikeId bikeId) {
        return !outOfStockBikeIds.contains(bikeId.value());
    }

    @Override
    public OrderId order(BikeId bikeId) {
        if (unavailableDealerBikeIds.contains(bikeId.value())) {
            throw new DealerUnavailableException(bikeId);
        }
        String prefix = nonCancellableBikeIds.contains(bikeId.value()) ? NON_CANCELLABLE_ORDER_PREFIX : ORDER_PREFIX;
        OrderId orderId = new OrderId(prefix + UUID.randomUUID());
        log.info("Placed order {} for bike {}", orderId.value(), bikeId.value());
        return orderId;
    }

    @Override
    public boolean requestCancellation(OrderId orderId) {
        log.info("Requesting cancellation of order {}", orderId.value());
        return !orderId.value().startsWith(NON_CANCELLABLE_ORDER_PREFIX);
    }

    @Override
    public void bookCancellationCosts(OrderId orderId) {
        // A real system would book the dealer's cancellation fee here.
        log.info("Booking cancellation costs for order {}", orderId.value());
    }

    /**
     * Signals that the (simulated) bike dealer could not be reached while placing an order. Thrown for
     * the {@code BIKE-FAIL} poison id to drive the reproducible outage demo. Nested so it stays colocated with
     * the demo logic without needing an outbound-adapter naming suffix.
     */
    public static class DealerUnavailableException extends RuntimeException {

        public DealerUnavailableException(BikeId bikeId) {
            super("Bike dealer API unavailable for " + bikeId.value() + " (simulated demo outage)");
        }
    }
}
