package io.miragon.blueprint.adapter.outbound.zeebe

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.support.UserTaskSupport
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes
import io.miragon.blueprint.application.port.outbound.TaskInboxPort
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.springframework.stereotype.Component

/**
 * Lists the open `Clarify alternative with customer` tasks from the [UserTaskSupport] pool and translates
 * them into the domain's business key. Zeebe has no business key, so the application id travels as a
 * process variable delivered with the task. It never leaks an engine task id upward — the inbox lists
 * cases, and cases are resolved through the domain, correlated by id.
 *
 * The pool is fed by the adapter's scheduled user-task delivery, so it lags the engine by the polling
 * interval plus the secondary storage's delay. A task that left the pool in the meantime is skipped.
 */
@Component
class TaskInboxAdapter(
    private val userTaskSupport: UserTaskSupport,
) : TaskInboxPort {

    private val applicationIdVariable = FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID.value

    override fun findOpenClarifications(): List<TaskInboxPort.OpenClarification> =
        userTaskSupport.getAllTasks()
            .filter { it.meta[CommonRestrictions.ACTIVITY_ID] == FlowNodes.UserTaskClarifyAlternative.id.value }
            .mapNotNull { task ->
                val applicationId = readApplicationId(task) ?: return@mapNotNull null
                val creationDate = task.getMetaValueAsOffsetDate(CREATION_DATE) ?: return@mapNotNull null
                TaskInboxPort.OpenClarification(
                    applicationId = ApplicationId.of(applicationId),
                    waitingSince = creationDate.toLocalDateTime(),
                )
            }

    private fun readApplicationId(task: TaskInformation): String? =
        runCatching { userTaskSupport.getPayload(task.taskId)[applicationIdVariable] as? String }.getOrNull()

    private companion object {
        /** Set by the adapter's `SCHEDULED` delivery only, see `docs/adr/0014`. */
        const val CREATION_DATE = "creationDate"
    }
}
