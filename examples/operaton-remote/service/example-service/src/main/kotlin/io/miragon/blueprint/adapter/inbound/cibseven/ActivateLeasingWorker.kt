package io.miragon.blueprint.adapter.inbound.cibseven

import io.miragon.blueprint.application.port.inbound.ActivateLeasingUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.ServiceTasks
import org.cibseven.bpm.client.spring.annotation.ExternalTaskSubscription
import org.cibseven.bpm.client.task.ExternalTask
import org.cibseven.bpm.client.task.ExternalTaskService
import org.springframework.stereotype.Component

/**
 * Flips the read model to ACTIVE once the withdrawal period has elapsed (serviceTask_activateLeasing →
 * endEvent_leasingActive). The embedded blueprint runs this from a message-end-event delegate; the
 * remote engine cannot reach into the worker's database, so activation is its own external service
 * task handled here — the idiomatic remote counterpart.
 */
@Component
@ExternalTaskSubscription(topicName = ServiceTasks.BIKE_LEASING_ACTIVATE_LEASING)
class ActivateLeasingWorker(
    private val useCase: ActivateLeasingUseCase,
) : BaseExternalTaskWorker() {

    override fun executeTask(externalTask: ExternalTask, externalTaskService: ExternalTaskService) {
        useCase.activate(ApplicationId.of(externalTask.businessKey))
        externalTaskService.complete(externalTask)
    }
}
