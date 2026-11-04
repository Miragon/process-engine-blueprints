package io.miragon.blueprint.adapter.inbound.zeebe

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import io.miragon.blueprint.adapter.process.ServiceTasks
import io.miragon.blueprint.application.port.inbound.CancelInsurancePolicyUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.springframework.stereotype.Component

@Component
class CancelPolicyWorker(
    private val useCase: CancelInsurancePolicyUseCase,
) {

    @ProcessEngineWorker(topic = ServiceTasks.MIRAVELO_CANCEL_POLICY)
    fun handle(@Variable applicationId: String) {
        useCase.cancelPolicy(ApplicationId.of(applicationId))
    }
}
