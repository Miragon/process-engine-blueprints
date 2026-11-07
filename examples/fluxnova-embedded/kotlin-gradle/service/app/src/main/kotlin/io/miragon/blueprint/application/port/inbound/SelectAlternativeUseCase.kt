package io.miragon.blueprint.application.port.inbound

import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.bike.BikeId

/**
 * Resolves the `Clarify alternative with customer` user task. When an alternative bike was found, the
 * newly chosen bike is carried into the re-order.
 */
interface SelectAlternativeUseCase {
    fun selectAlternative(command: Command)

    data class Command(
        val applicationId: ApplicationId,
        val alternativeFound: Boolean,
        val bikeId: BikeId? = null,
        val bikeModel: String? = null,
    ) {
        init {
            require(!alternativeFound || bikeId != null) { "An accepted alternative must name the bike" }
        }
    }
}
