package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.vanillabp.spi.service.TaskEvent
import io.vanillabp.spi.service.TaskId
import io.vanillabp.spi.service.WorkflowTask
import org.springframework.stereotype.Component

@Component
class ClarifyReturnTask : BikeLeasingWorkflow() {

    @WorkflowTask(taskDefinition = "clarifyReturn")
    fun clarifyReturn(
        aggregate: LeasingApplicationAggregate,
        @TaskId taskId: String,
        @TaskEvent event: TaskEvent.Event,
    ) {
        when (event) {
            TaskEvent.Event.CREATED -> aggregate.returnClarificationRequested(taskId)
            else -> aggregate.returnClarificationCancelled()
        }
    }
}
