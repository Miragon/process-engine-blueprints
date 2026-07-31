package io.miragon.blueprint.adapter.inbound.cibseven

import io.miragon.blueprint.application.port.inbound.CancelContractUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.ServiceTasks
import org.cibseven.bpm.client.spring.annotation.ExternalTaskSubscription
import org.cibseven.bpm.client.task.ExternalTask
import org.cibseven.bpm.client.task.ExternalTaskService
import org.springframework.stereotype.Component

@Component
@ExternalTaskSubscription(topicName = ServiceTasks.BIKE_LEASING_CANCEL_CONTRACT)
class CancelContractWorker(
    private val useCase: CancelContractUseCase,
) : BaseExternalTaskWorker() {

    override fun executeTask(externalTask: ExternalTask, externalTaskService: ExternalTaskService) {
        useCase.cancelContract(ApplicationId.of(externalTask.businessKey))
        externalTaskService.complete(externalTask)
    }
}
