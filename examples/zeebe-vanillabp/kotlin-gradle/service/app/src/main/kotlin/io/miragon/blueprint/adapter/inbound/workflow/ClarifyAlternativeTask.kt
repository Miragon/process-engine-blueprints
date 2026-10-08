package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.vanillabp.spi.service.TaskEvent
import io.vanillabp.spi.service.TaskId
import io.vanillabp.spi.service.WorkflowTask
import java.time.Clock
import java.time.LocalDateTime
import org.springframework.stereotype.Component

@Component
class ClarifyAlternativeTask(
    private val clock: Clock,
) : BikeLeasingWorkflow() {

    @WorkflowTask(taskDefinition = "clarifyAlternative")
    fun clarifyAlternative(
        aggregate: LeasingApplicationAggregate,
        @TaskId taskId: String,
        @TaskEvent event: TaskEvent.Event,
    ) {
        when (event) {
            TaskEvent.Event.CREATED -> aggregate.clarificationRequested(taskId, LocalDateTime.now(clock))
            else -> aggregate.clarificationCancelled()
        }
    }
}
