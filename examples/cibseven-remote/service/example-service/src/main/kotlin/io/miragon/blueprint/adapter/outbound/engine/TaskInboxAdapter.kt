package io.miragon.blueprint.adapter.outbound.engine

import io.miragon.blueprint.application.port.outbound.TaskInboxPort
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.Elements
import org.cibseven.rest.client.api.ProcessInstanceApi
import org.cibseven.rest.client.api.TaskApi
import org.springframework.stereotype.Component
import java.time.ZoneId

/**
 * Reads the open `Clarify alternative with customer` tasks from the *remote* engine over its REST API
 * (via the generated [TaskApi]) and translates them into the domain's business key (the application
 * id). It never leaks an engine task id upward: the inbox lists cases, and cases are resolved through
 * the domain, correlated by id.
 *
 * The engine's task list does not carry the business key on the task itself, so each task's process
 * instance is resolved through [ProcessInstanceApi] to recover the application id — the remote
 * counterpart to the embedded blueprint reading it straight from `RuntimeService`.
 */
@Component
class TaskInboxAdapter(
    private val taskApi: TaskApi,
    private val processInstanceApi: ProcessInstanceApi,
) : TaskInboxPort {

    override fun findOpenClarifications(): List<TaskInboxPort.OpenClarification> {
        val tasks = taskApi.getTasks(taskDefinitionKey = Elements.USER_TASK_CLARIFY_ALTERNATIVE.value)
        return tasks.mapNotNull { task ->
            val processInstanceId = task.processInstanceId ?: return@mapNotNull null
            val businessKey = processInstanceApi.getProcessInstance(processInstanceId).businessKey
                ?: return@mapNotNull null
            val created = task.created ?: return@mapNotNull null
            TaskInboxPort.OpenClarification(
                applicationId = ApplicationId.of(businessKey),
                waitingSince = created.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(),
            )
        }
    }
}
