package io.miragon.blueprint.adapter.process

import io.temporal.activity.ActivityOptions
import io.temporal.spring.boot.WorkflowImpl
import io.temporal.workflow.Workflow
import java.time.Duration

@WorkflowImpl(taskQueues = [BIKE_LEASING_TASK_QUEUE])
class CancelBikeOrderWorkflowImpl : CancelBikeOrderWorkflow {

    private val activityOptions = ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(30)).build()
    private val requestCancellation =
        Workflow.newActivityStub(CancelBikeOrderActivities.RequestCancellation::class.java, activityOptions)
    private val bookCosts = Workflow.newActivityStub(CancelBikeOrderActivities.BookCosts::class.java, activityOptions)

    private var returnClarified = false

    override fun run(orderId: String) {
        val cancellationPossible = requestCancellation.requestCancellation(orderId)
        if (!cancellationPossible) {
            Workflow.await { returnClarified }
            bookCosts.bookCosts(orderId)
        }
    }

    override fun returnClarified() {
        returnClarified = true
    }
}
