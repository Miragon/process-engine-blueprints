package io.miragon.common.test.temporal

import io.temporal.api.common.v1.WorkflowExecution
import io.temporal.api.enums.v1.EventType
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest
import io.temporal.testing.TestWorkflowEnvironment
import org.awaitility.Awaitility.await
import java.time.Duration

private val TIMEOUT: Duration = Duration.ofSeconds(10)

fun TestWorkflowEnvironment.awaitActivitySettled(workflowId: String, activityType: String) {
    val request = DescribeWorkflowExecutionRequest.newBuilder()
        .setNamespace(namespace)
        .setExecution(WorkflowExecution.newBuilder().setWorkflowId(workflowId))
        .build()
    await().atMost(TIMEOUT).until {
        workflowServiceStubs.blockingStub().describeWorkflowExecution(request)
            .pendingActivitiesList.none { it.activityType.name == activityType }
    }
}

fun TestWorkflowEnvironment.awaitCancelRequested(workflowId: String) {
    await().atMost(TIMEOUT).until {
        workflowClient.fetchHistory(workflowId).events
            .any { it.eventType == EventType.EVENT_TYPE_ACTIVITY_TASK_CANCEL_REQUESTED }
    }
}
