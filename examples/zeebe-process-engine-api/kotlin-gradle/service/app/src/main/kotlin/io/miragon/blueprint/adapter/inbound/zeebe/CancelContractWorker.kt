package io.miragon.blueprint.adapter.inbound.zeebe

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import io.miragon.blueprint.adapter.process.ServiceTasks
import io.miragon.blueprint.application.port.inbound.CancelContractUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.springframework.stereotype.Component

@Component
class CancelContractWorker(
    private val useCase: CancelContractUseCase,
) {

    @ProcessEngineWorker(topic = ServiceTasks.MIRAVELO_CANCEL_CONTRACT)
    fun handle(@Variable applicationId: String) {
        useCase.cancelContract(ApplicationId.of(applicationId))
    }
}
