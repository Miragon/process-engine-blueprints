package io.miragon.blueprint.adapter.inbound.cibseven

import io.miragon.blueprint.application.port.inbound.ValidateApplicationUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.leasing.ApplicationInvalidException
import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.ServiceTasks
import org.cibseven.bpm.client.spring.annotation.ExternalTaskSubscription
import org.cibseven.bpm.client.task.ExternalTask
import org.cibseven.bpm.client.task.ExternalTaskService
import org.springframework.stereotype.Component

@Component
@ExternalTaskSubscription(topicName = ServiceTasks.BIKE_LEASING_VALIDATE_APPLICATION)
class ValidateApplicationWorker(
    private val useCase: ValidateApplicationUseCase,
) : BaseExternalTaskWorker() {

    override fun executeTask(externalTask: ExternalTask, externalTaskService: ExternalTaskService) {
        try {
            useCase.validate(ApplicationId.of(externalTask.businessKey))
        } catch (e: ApplicationInvalidException) {
            // Raise the `applicationInvalid` BPMN error so the error boundary event diverts to rejection.
            externalTaskService.handleBpmnError(externalTask, "applicationInvalid", e.reason)
            return
        }
        externalTaskService.complete(externalTask)
    }
}
