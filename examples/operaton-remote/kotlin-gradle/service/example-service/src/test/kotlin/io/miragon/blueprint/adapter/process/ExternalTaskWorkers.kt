package io.miragon.blueprint.adapter.process

import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.until
import org.operaton.bpm.client.ExternalTaskClient
import org.operaton.bpm.client.spring.SpringTopicSubscription
import org.operaton.bpm.engine.ProcessEngine
import org.operaton.bpm.engine.externaltask.ExternalTask
import org.springframework.boot.test.context.TestComponent
import java.time.Duration

/**
 * Runs this service's real external-task workers one task at a time — the remote counterpart to the
 * embedded blueprint's `executeJobFor(activity)`. It polls with the worker's own Spring subscription until the
 * worker has handled the waiting task over `/engine-rest`, then runs the engine's async continuations.
 */
@TestComponent
class ExternalTaskWorkers(
    private val engine: ProcessEngine,
    private val client: ExternalTaskClient,
    subscriptions: List<SpringTopicSubscription>,
) {

    private val subscriptionByTopic = subscriptions.associateBy { it.topicName }

    fun execute(topicName: String) {
        runWorkerOnWaitingTask(topicName)
        engine.executeAsyncContinuations()
    }

    /** Lets the workers handle the waiting tasks in engine order — for chains such as compensation. */
    fun drainToWaitState() {
        engine.executeAsyncContinuations()
        generateSequence { nextWaitingTopic() }.forEach(::execute)
    }

    private fun runWorkerOnWaitingTask(topicName: String) {
        val taskId = singleWaitingTaskId(topicName)
        val subscription = subscriptionOf(topicName)
        startPolling(subscription)
        try {
            await atMost WORKER_TIMEOUT until { isHandled(taskId) }
        } finally {
            stopPolling(subscription)
        }
    }

    private fun startPolling(subscription: SpringTopicSubscription) {
        subscription.open()
        client.start()
    }

    /** Stopping waits for the poll in flight, so no later task is fetched behind the test's back. */
    private fun stopPolling(subscription: SpringTopicSubscription) {
        client.stop()
        subscription.close()
    }

    private fun isHandled(taskId: String): Boolean {
        val openTask = externalTasks().externalTaskId(taskId).singleResult() ?: return true
        failIfWorkerFailed(openTask)
        return false
    }

    private fun failIfWorkerFailed(task: ExternalTask) {
        check(task.errorMessage == null) { "worker of '${task.topicName}' failed: ${task.errorMessage}" }
    }

    private fun singleWaitingTaskId(topicName: String): String {
        val waitingTasks = externalTasks().topicName(topicName).notLocked().list()
        require(waitingTasks.size == 1) { "expected one waiting task of '$topicName', found ${waitingTasks.size}" }
        return waitingTasks.single().id
    }

    private fun nextWaitingTopic(): String? = externalTasks().notLocked().listPage(0, 1).firstOrNull()?.topicName

    private fun subscriptionOf(topicName: String): SpringTopicSubscription {
        return requireNotNull(subscriptionByTopic[topicName]) { "no worker subscribes to '$topicName'" }
    }

    private fun externalTasks() = engine.externalTaskService.createExternalTaskQuery()

    private companion object {
        val WORKER_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
