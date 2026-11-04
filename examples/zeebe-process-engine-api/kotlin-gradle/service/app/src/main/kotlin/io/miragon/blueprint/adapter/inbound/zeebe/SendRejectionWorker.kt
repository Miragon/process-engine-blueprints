package io.miragon.blueprint.adapter.inbound.zeebe

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import io.miragon.blueprint.adapter.process.ServiceTasks
import io.miragon.blueprint.application.port.inbound.RejectApplicationUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.springframework.stereotype.Component

@Component
class SendRejectionWorker(
    private val useCase: RejectApplicationUseCase,
) {

    @ProcessEngineWorker(topic = ServiceTasks.MIRAVELO_SEND_REJECTION)
    fun handle(@Variable applicationId: String) {
        useCase.reject(ApplicationId.of(applicationId))
    }
}
