package io.miragon.blueprint.domain.bike

/**
 * Raised when the dealer cannot deliver the requested bike, so no order was placed. The inbound
 * adapter translates this into a business failure, which the process handles by clarifying an
 * alternative with the customer.
 */
class BikeUnavailableException(
    bikeId: BikeId,
) : RuntimeException("Bike ${bikeId.value} is not available at the dealer")
