package io.miragon.blueprint.process

import io.miragon.bpmn.runtime.ElementId
import io.miragon.bpmn.runtime.MessageName
import org.assertj.core.api.Assertions.assertThat
import org.cibseven.bpm.engine.ProcessEngine
import org.cibseven.bpm.engine.runtime.ProcessInstance

/** Worker id the tests lock external tasks with — stands in for the remote worker. */
private const val TEST_WORKER = "test-worker"

/** Lock duration for tests; irrelevant because tasks are completed immediately after locking. */
private const val TEST_LOCK_DURATION_MS = 60_000L

/**
 * Completes the **single** waiting external task of [topicName] — the remote counterpart to the
 * embedded blueprint's `executeJobFor(activityId)`. It is explicit about *which* task it drives and
 * fails loudly if that task is not (uniquely) waiting, so a test reads as an ordered trace. The
 * [variables] are passed as the task's output — exactly what a real worker would return (e.g.
 * `orderBike` → `bikeAvailable` / `orderId`). Afterwards it settles the deterministic async plumbing
 * (asyncAfter jobs, the DMN, gateways) up to the next wait state or external task.
 */
fun ProcessEngine.completeExternalTask(topicName: String, variables: Map<String, Any?> = emptyMap()) {
    val tasks = externalTaskService.createExternalTaskQuery().topicName(topicName).notLocked().list()
    require(tasks.size == 1) {
        "expected exactly one waiting external task for topic '$topicName', found ${tasks.size}"
    }
    val task = tasks.single()
    externalTaskService.lock(task.id, TEST_WORKER, TEST_LOCK_DURATION_MS)
    externalTaskService.complete(task.id, TEST_WORKER, variables)
    executeAsyncContinuations()
}

/** Completes the single waiting user task with [taskDefinitionKey], then settles the plumbing. */
fun ProcessEngine.completeUserTask(taskDefinitionKey: String, variables: Map<String, Any?> = emptyMap()) {
    val task = taskService.createTaskQuery().taskDefinitionKey(taskDefinitionKey).singleResult()
    requireNotNull(task) { "no waiting user task '$taskDefinitionKey'" }
    taskService.complete(task.id, variables)
    executeAsyncContinuations()
}

/** Correlates [message] to the instance identified by [businessKey], then settles the plumbing. */
fun ProcessEngine.correlateMessage(message: MessageName, businessKey: String) {
    runtimeService.createMessageCorrelation(message.value).processInstanceBusinessKey(businessKey).correlate()
    executeAsyncContinuations()
}

/**
 * Fires the timer job of the given boundary/catch event directly, regardless of its due date — the
 * tests verify the timer path is wired correctly, not the real-world waiting duration.
 */
fun ProcessEngine.fireTimer(timerActivityId: ElementId) {
    val timer = managementService.createJobQuery().timers().activityId(timerActivityId.value).singleResult()
    requireNotNull(timer) { "no timer job found for activity '${timerActivityId.value}'" }
    managementService.executeJob(timer.id)
    executeAsyncContinuations()
}

/**
 * Generic fallback for **engine-ordered** chains — e.g. compensation, whose handlers run in an
 * implementation-defined order where enumerating each external task would be brittle. It drives async
 * jobs *and* completes whatever external tasks are waiting (with the per-topic [externalTaskOutputs])
 * until the next wait state. Prefer [completeExternalTask] for the deterministic, linear parts of a flow.
 */
fun ProcessEngine.drainToWaitState(
    externalTaskOutputs: Map<String, Map<String, Any?>> = emptyMap(),
    maxIterations: Int = 100,
) {
    repeat(maxIterations) {
        val job = managementService.createJobQuery().active().messages().listPage(0, 1).firstOrNull()
        if (job != null) {
            managementService.executeJob(job.id)
            return@repeat
        }
        val task = externalTaskService.createExternalTaskQuery().notLocked().listPage(0, 1).firstOrNull()
            ?: return
        externalTaskService.lock(task.id, TEST_WORKER, TEST_LOCK_DURATION_MS)
        externalTaskService.complete(task.id, TEST_WORKER, externalTaskOutputs[task.topicName] ?: emptyMap())
    }
    error("process did not reach a wait state within $maxIterations iterations")
}

/** Starts the process through its message start event, keyed by [businessKey], then settles the plumbing. */
fun ProcessEngine.startLeasing(businessKey: String, age: Int, income: Double, bikeId: String) {
    val start = BikeLeasingProcessProcessApi.Variables.StartEventLeasingRequestReceived
    runtimeService.startProcessInstanceByMessage(
        BikeLeasingProcessProcessApi.Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.value,
        businessKey,
        mapOf(
            start.APPLICATION_ID.value to businessKey,
            start.BIKE_ID.value to bikeId,
            start.MONTHLY_NET_INCOME.value to income,
            start.AGE.value to age,
        ),
    )
    executeAsyncContinuations()
}

/** Finds the bike-leasing process instance for the given business key. Fails the test if none exists. */
fun ProcessEngine.findInstance(businessKey: String): ProcessInstance {
    val instance = runtimeService.createProcessInstanceQuery()
        .processDefinitionKey(BikeLeasingProcessProcessApi.PROCESS_ID.value)
        .processInstanceBusinessKey(businessKey)
        .singleResult()
    assertThat(instance).`as`("process instance for business key %s", businessKey).isNotNull
    return instance
}

/**
 * Drives all pending async-continuation jobs (start event, DMN, gateways, `asyncAfter`) until the
 * process settles at its next wait state or external task. Timer jobs are excluded — those are fired
 * explicitly via [fireTimer].
 */
private fun ProcessEngine.executeAsyncContinuations(maxIterations: Int = 100) {
    repeat(maxIterations) {
        val job = managementService.createJobQuery().active().messages().listPage(0, 1).firstOrNull()
            ?: return
        managementService.executeJob(job.id)
    }
    error("async continuations did not settle within $maxIterations iterations")
}
