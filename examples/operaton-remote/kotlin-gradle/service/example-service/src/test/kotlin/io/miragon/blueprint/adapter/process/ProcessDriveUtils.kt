package io.miragon.blueprint.adapter.process

import io.miragon.bpmn.runtime.BpmnErrorDefinition
import io.miragon.bpmn.runtime.FlowNode
import io.miragon.bpmn.runtime.MessageName
import org.operaton.bpm.engine.ProcessEngine
import org.operaton.bpm.engine.externaltask.ExternalTask
import org.operaton.bpm.engine.runtime.ProcessInstance

/** Worker id the tests lock external tasks with — stands in for the remote worker. */
private const val TEST_WORKER = "test-worker"

/** Lock duration for tests; irrelevant because tasks are completed immediately after locking. */
private const val TEST_LOCK_DURATION_MS = 60_000L

/**
 * Completes the **single** waiting external task of [topicName], as the remote worker would. It is
 * explicit about *which* task it drives and fails loudly if that task is not (uniquely) waiting, so a
 * test reads as an ordered trace. The [variables] are passed as the task's output — exactly what a real
 * worker would return (e.g. `orderBike` → `orderId`). It only ends the external task: the test runs the
 * async continuation that follows with its own `execute(job(...))`.
 */
fun ProcessEngine.completeExternalTask(topicName: String, variables: Map<String, Any> = emptyMap()) {
    val task = lockSingleExternalTask(topicName)
    externalTaskService.complete(task.id, TEST_WORKER, variables)
}

/**
 * Ends the **single** waiting external task of [topicName] with the BPMN [error] — what a real worker
 * does through `handleBpmnError` — so the task is left through its error boundary event instead of
 * being completed. Like [completeExternalTask], it runs no job.
 */
fun ProcessEngine.raiseBpmnErrorOnExternalTask(topicName: String, error: BpmnErrorDefinition) {
    val task = lockSingleExternalTask(topicName)
    externalTaskService.handleBpmnError(task.id, TEST_WORKER, error.code)
}

private fun ProcessEngine.lockSingleExternalTask(topicName: String): ExternalTask {
    val tasks = externalTaskService.createExternalTaskQuery().topicName(topicName).notLocked().list()
    require(tasks.size == 1) {
        "expected exactly one waiting external task for topic '$topicName', found ${tasks.size}"
    }
    val task = tasks.single()
    externalTaskService.lock(task.id, TEST_WORKER, TEST_LOCK_DURATION_MS)
    return task
}

/** Completes the single waiting user task with [taskDefinitionKey]. */
fun ProcessEngine.completeUserTask(taskDefinitionKey: String, variables: Map<String, Any> = emptyMap()) {
    val task = taskService.createTaskQuery().taskDefinitionKey(taskDefinitionKey).singleResult()
    requireNotNull(task) { "no waiting user task '$taskDefinitionKey'" }
    taskService.complete(task.id, variables)
}

/** Correlates [message] to the instance identified by [businessKey]. */
fun ProcessEngine.correlateMessage(message: MessageName, businessKey: String) {
    runtimeService.createMessageCorrelation(message.value).processInstanceBusinessKey(businessKey).correlate()
}

/**
 * Fires the timer job of the given boundary/catch event directly, regardless of its due date — the
 * tests verify the timer path is wired correctly, not the real-world waiting duration.
 */
fun ProcessEngine.fireTimer(timerEvent: FlowNode) {
    val timer = managementService.createJobQuery().timers().activityId(timerEvent.id.value).singleResult()
    requireNotNull(timer) { "no timer job found for activity '${timerEvent.id.value}'" }
    managementService.executeJob(timer.id)
}

/** Starts the process through its message start event, keyed by [businessKey]. */
fun ProcessEngine.startLeasing(businessKey: String, age: Int, income: Double, bikeId: String) {
    val start = BikeLeasingProcessProcessApi.FlowNodes.StartEventLeasingRequestReceived.Variables
    runtimeService.startProcessInstanceByMessage(
        Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.value,
        businessKey,
        mapOf(
            start.APPLICATION_ID.value to businessKey,
            start.BIKE_ID.value to bikeId,
            start.MONTHLY_NET_INCOME.value to income,
            start.AGE.value to age,
        ),
    )
}

/** Finds the bike-leasing process instance for the given business key. Fails the test if none exists. */
fun ProcessEngine.findInstance(businessKey: String): ProcessInstance {
    val instance = runtimeService.createProcessInstanceQuery()
        .processDefinitionKey(BikeLeasingProcessProcessApi.PROCESS_ID.value)
        .processInstanceBusinessKey(businessKey)
        .singleResult()
    return requireNotNull(instance) { "no process instance for business key $businessKey" }
}
