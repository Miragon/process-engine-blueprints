package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.ServiceTasks
import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.inbound.ActivateLeasingUseCase
import io.vanillabp.spi.service.WorkflowTask
import org.springframework.stereotype.Component

@Component
class ActivateLeasingTask(
    private val useCase: ActivateLeasingUseCase,
) : BikeLeasingWorkflow() {

    @WorkflowTask(taskDefinition = ServiceTasks.MIRAVELO_ACTIVATE_LEASING)
    fun activateLeasing(aggregate: LeasingApplicationAggregate) {
        aggregate.adopt(useCase.activate(aggregate.toDomain()))
    }
}
