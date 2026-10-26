package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.vanillabp.spi.service.TaskEvent
import io.vanillabp.spi.service.WorkflowTask
import mu.KotlinLogging
import org.springframework.stereotype.Component

@Component
class ClarifyReturnTask : BikeLeasingWorkflow() {

    private val log = KotlinLogging.logger {}

    @WorkflowTask(taskDefinition = "clarifyReturn")
    fun clarifyReturn(
        aggregate: LeasingApplicationAggregate,
        @TaskEvent event: TaskEvent.Event,
    ) {
        log.info { "Return clarification with the supplier $event for application ${aggregate.applicationId}" }
    }
}
