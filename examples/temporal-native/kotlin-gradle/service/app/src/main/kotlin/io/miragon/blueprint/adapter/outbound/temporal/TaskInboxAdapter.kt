package io.miragon.blueprint.adapter.outbound.temporal

import io.miragon.blueprint.adapter.process.CLARIFICATION_WAITING_SINCE
import io.miragon.blueprint.application.port.outbound.TaskInboxPort
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.temporal.client.WorkflowClient
import org.springframework.stereotype.Component

/**
 * Reads the open clarifications from Temporal's visibility store: the workflow sets the search
 * attribute while it waits for the decision and clears it afterwards.
 */
@Component
class TaskInboxAdapter(
    private val client: WorkflowClient,
) : TaskInboxPort {

    override fun findOpenClarifications(): List<TaskInboxPort.OpenClarification> =
        client.listExecutions(OPEN_CLARIFICATIONS)
            .map { execution ->
                TaskInboxPort.OpenClarification(
                    applicationId = ApplicationId.of(execution.execution.workflowId),
                    waitingSince = execution.typedSearchAttributes.get(CLARIFICATION_WAITING_SINCE).toLocalDateTime(),
                )
            }
            .toList()

    private companion object {
        val OPEN_CLARIFICATIONS = "${CLARIFICATION_WAITING_SINCE.name} IS NOT NULL AND ExecutionStatus = 'Running'"
    }
}
