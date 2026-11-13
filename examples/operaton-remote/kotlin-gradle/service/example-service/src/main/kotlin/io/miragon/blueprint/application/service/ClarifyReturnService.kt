package io.miragon.blueprint.application.service

import io.miragon.blueprint.application.port.inbound.ClarifyReturnUseCase
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository
import io.miragon.blueprint.application.port.outbound.LeasingProcess
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ClarifyReturnService(
    private val repository: LeasingApplicationRepository,
    private val process: LeasingProcess,
) : ClarifyReturnUseCase {

    override fun clarifyReturn(id: ApplicationId) {
        repository.findById(id) ?: error("Unknown application $id")
        process.completeReturnClarification(id)
    }
}
