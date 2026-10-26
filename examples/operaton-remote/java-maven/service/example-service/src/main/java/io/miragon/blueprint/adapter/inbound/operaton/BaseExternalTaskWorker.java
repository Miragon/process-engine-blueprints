package io.miragon.blueprint.adapter.inbound.operaton;

import org.operaton.bpm.client.task.ExternalTask;
import org.operaton.bpm.client.task.ExternalTaskHandler;
import org.operaton.bpm.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base for all remote external-task workers — the remote counterpart to the embedded blueprint's
 * {@code BaseDelegate}. It wraps the actual work and, on an unexpected exception, reports the task as a
 * <strong>failure</strong> back to the remote engine ({@code handleFailure}). Retries are the remote
 * engine's equivalent of a delegate's {@code failedJobRetryTimeCycle}: instead of a BPMN attribute, the
 * worker decides how many times a failed task is re-attempted and how long the engine waits between
 * attempts. The defaults mean "fail once, raise the incident immediately"; a worker overrides
 * {@link #failureRetries()} / {@link #failureRetryTimeoutMs()} to get a visible retry countdown before
 * the incident (see {@code OrderBikeWorker}).
 *
 * <p>The concrete worker is responsible for calling {@code externalTaskService.complete(...)} on success
 * (so it can pass output variables, mirroring {@code DelegateExecution.setVariable}) and may call
 * {@code externalTaskService.handleBpmnError(...)} to raise a BPMN error caught by a boundary event.
 */
public abstract class BaseExternalTaskWorker implements ExternalTaskHandler {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** Retries granted on the first failure before the engine raises an incident. 0 = fail immediately. */
    protected int failureRetries() {
        return 0;
    }

    /** How long the engine waits before making a failed task available again, in milliseconds. */
    protected long failureRetryTimeoutMs() {
        return 0L;
    }

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        try {
            executeTask(externalTask, externalTaskService);
        } catch (Exception e) {
            log.error("Error while processing external task '{}'", externalTask.getTopicName(), e);
            // On the first failure the engine reports `retries == null`, so grant the full budget; on
            // each subsequent failure decrement it. At 0 the engine raises the incident.
            Integer retries = externalTask.getRetries();
            int remainingRetries = retries != null ? retries - 1 : failureRetries();
            externalTaskService.handleFailure(
                    externalTask,
                    e.getMessage() != null ? e.getMessage() : "Error while processing external task",
                    stackTraceToString(e),
                    Math.max(remainingRetries, 0),
                    failureRetryTimeoutMs());
        }
    }

    protected abstract void executeTask(ExternalTask externalTask, ExternalTaskService externalTaskService);

    private static String stackTraceToString(Throwable throwable) {
        java.io.StringWriter writer = new java.io.StringWriter();
        throwable.printStackTrace(new java.io.PrintWriter(writer));
        return writer.toString();
    }
}
