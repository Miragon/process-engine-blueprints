package io.miragon.blueprint.adapter.inbound.operaton

import io.miragon.blueprint.application.port.inbound.SendCancellationConfirmationUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.process.ServiceTasks
import org.operaton.bpm.client.spring.annotation.ExternalTaskSubscription
import org.operaton.bpm.client.task.ExternalTask
import org.operaton.bpm.client.task.ExternalTaskService
import org.springframework.stereotype.Component

@Component
@ExternalTaskSubscription(topicName = ServiceTasks.BIKE_LEASING_SEND_CANCELLATION_CONFIRMATION)
class SendCancellationConfirmationWorker(
    private val useCase: SendCancellationConfirmationUseCase,
) : BaseExternalTaskWorker() {

    override fun executeTask(externalTask: ExternalTask, externalTaskService: ExternalTaskService) {
        useCase.sendCancellationConfirmation(ApplicationId.of(externalTask.businessKey))
        externalTaskService.complete(externalTask)
    }
}
