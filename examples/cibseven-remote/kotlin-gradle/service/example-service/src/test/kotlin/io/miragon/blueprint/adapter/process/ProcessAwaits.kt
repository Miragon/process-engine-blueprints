package io.miragon.blueprint.adapter.process

import io.miragon.bpmn.runtime.FlowNode
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.cibseven.bpm.engine.ProcessEngine
import org.cibseven.bpm.engine.runtime.Job
import org.cibseven.bpm.engine.runtime.ProcessInstance
import org.cibseven.bpm.engine.task.Task
import org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat
import java.time.Duration

private val PROCESS_TIMEOUT: Duration = Duration.ofSeconds(15)

fun awaitWaitingAt(instance: ProcessInstance, node: FlowNode) = awaitAsserted { assertThat(instance).isWaitingAt(node.id.value) }

fun awaitPassed(instance: ProcessInstance, node: FlowNode) = awaitAsserted { assertThat(instance).hasPassed(node.id.value) }

fun awaitEnded(instance: ProcessInstance) = awaitAsserted { assertThat(instance).isEnded }

fun ProcessEngine.awaitUserTask(taskKey: String) = awaitAsserted { assertThat(userTask(taskKey)).isNotNull }

/** Fires the timer of [timerEvent] now and leaves what follows to the job executor, unlike [fireTimer]. */
fun ProcessEngine.fireTimerAndContinueInBackground(timerEvent: FlowNode) = managementService.executeJob(timerJobOf(timerEvent).id)

private fun awaitAsserted(assertion: () -> Unit) = await atMost PROCESS_TIMEOUT untilAsserted assertion

private fun ProcessEngine.userTask(taskKey: String): Task? = taskService.createTaskQuery().taskDefinitionKey(taskKey).singleResult()

private fun ProcessEngine.timerJobOf(timerEvent: FlowNode): Job {
    val timer = managementService.createJobQuery().timers().activityId(timerEvent.id.value).singleResult()
    return requireNotNull(timer) { "no timer job of '${timerEvent.id.value}'" }
}
