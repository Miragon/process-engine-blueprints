package io.miragon.blueprint.application.port.inbound

import io.miragon.blueprint.domain.leasing.ApplicationId

/**
 * Resolves the `Clarify return with supplier` user task: the back office confirms that the return of
 * an order the dealer could not cancel is arranged with the supplier, so the costs can be booked.
 */
interface ClarifyReturnUseCase {
    fun clarifyReturn(id: ApplicationId)
}
