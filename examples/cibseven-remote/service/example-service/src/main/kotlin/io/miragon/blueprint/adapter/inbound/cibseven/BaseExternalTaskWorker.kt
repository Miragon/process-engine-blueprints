package io.miragon.blueprint.adapter.inbound.cibseven

import mu.KotlinLogging
import org.cibseven.bpm.client.task.ExternalTask
import org.cibseven.bpm.client.task.ExternalTaskHandler
import org.cibseven.bpm.client.task.ExternalTaskService

/**
 * Base for all remote external-task workers — the remote counterpart to the embedded blueprint's
 * `BaseDelegate`. It wraps the actual work and, on an unexpected exception, reports the task as a
 * **failure** back to the remote engine (`handleFailure`, no retries here — tune per worker).
 *
 * The concrete worker is responsible for calling `externalTaskService.complete(...)` on success (so it
 * can pass output variables, mirroring `DelegateExecution.setVariable`) and may call
 * `externalTaskService.handleBpmnError(...)` to raise a BPMN error caught by a boundary event.
 */
abstract class BaseExternalTaskWorker : ExternalTaskHandler {

    protected val log = KotlinLogging.logger {}

    override fun execute(externalTask: ExternalTask, externalTaskService: ExternalTaskService) {
        try {
            executeTask(externalTask, externalTaskService)
        } catch (e: Exception) {
            log.error(e) { "Error while processing external task '${externalTask.topicName}'" }
            externalTaskService.handleFailure(
                externalTask,
                e.message ?: "Error while processing external task",
                e.stackTraceToString(),
                0,
                0L,
            )
        }
    }

    abstract fun executeTask(externalTask: ExternalTask, externalTaskService: ExternalTaskService)
}
