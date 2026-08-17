package io.miragon.blueprint.adapter.inbound.cibseven

import mu.KotlinLogging
import org.cibseven.bpm.client.task.ExternalTask
import org.cibseven.bpm.client.task.ExternalTaskHandler
import org.cibseven.bpm.client.task.ExternalTaskService

/**
 * Base for all remote external-task workers — the remote counterpart to the embedded blueprint's
 * `BaseDelegate`. It wraps the actual work and, on an unexpected exception, reports the task as a
 * **failure** back to the remote engine (`handleFailure`). Retries are the remote engine's equivalent
 * of a delegate's `failedJobRetryTimeCycle`: instead of a BPMN attribute, the worker decides how many
 * times a failed task is re-attempted and how long the engine waits between attempts. The defaults
 * mean "fail once, raise the incident immediately"; a worker overrides [failureRetries] /
 * [failureRetryTimeoutMs] to get a visible retry countdown before the incident (see [OrderBikeWorker]).
 *
 * The concrete worker is responsible for calling `externalTaskService.complete(...)` on success (so it
 * can pass output variables, mirroring `DelegateExecution.setVariable`) and may call
 * `externalTaskService.handleBpmnError(...)` to raise a BPMN error caught by a boundary event.
 */
abstract class BaseExternalTaskWorker : ExternalTaskHandler {

    protected val log = KotlinLogging.logger {}

    /** Retries granted on the first failure before the engine raises an incident. 0 = fail immediately. */
    protected open val failureRetries: Int = 0

    /** How long the engine waits before making a failed task available again, in milliseconds. */
    protected open val failureRetryTimeoutMs: Long = 0L

    override fun execute(externalTask: ExternalTask, externalTaskService: ExternalTaskService) {
        try {
            executeTask(externalTask, externalTaskService)
        } catch (e: Exception) {
            log.error(e) { "Error while processing external task '${externalTask.topicName}'" }
            // On the first failure the engine reports `retries == null`, so grant the full budget; on
            // each subsequent failure decrement it. At 0 the engine raises the incident.
            val remainingRetries = externalTask.retries?.let { it - 1 } ?: failureRetries
            externalTaskService.handleFailure(
                externalTask,
                e.message ?: "Error while processing external task",
                e.stackTraceToString(),
                remainingRetries.coerceAtLeast(0),
                failureRetryTimeoutMs,
            )
        }
    }

    abstract fun executeTask(externalTask: ExternalTask, externalTaskService: ExternalTaskService)
}
