package io.miragon.blueprint.adapter.process

import io.temporal.activity.ActivityOptions
import io.temporal.spring.boot.WorkflowImpl
import io.temporal.workflow.Workflow
import java.time.Duration

/**
 * `cancellationPossible` is ported as modelled in `cancel-bike-order.bpmn`, where `true` leads to the
 * return clarification and `false` ends the workflow.
 */
@WorkflowImpl(taskQueues = [BIKE_LEASING_TASK_QUEUE])
class CancelBikeOrderWorkflowImpl : CancelBikeOrderWorkflow {

    private val activityOptions = ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(30)).build()
    private val requestCancellation = Workflow.newActivityStub(Activities.RequestCancellation::class.java, activityOptions)
    private val bookCosts = Workflow.newActivityStub(Activities.BookCosts::class.java, activityOptions)

    private var returnClarified = false

    override fun run(orderId: String) {
        if (requestCancellation.requestCancellation(orderId)) {
            Workflow.await { returnClarified }
            bookCosts.bookCosts(orderId)
        }
    }

    override fun returnClarified() {
        returnClarified = true
    }
}
