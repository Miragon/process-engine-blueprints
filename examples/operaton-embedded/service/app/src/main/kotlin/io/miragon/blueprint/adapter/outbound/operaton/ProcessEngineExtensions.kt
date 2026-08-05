package io.miragon.blueprint.adapter.outbound.operaton

import org.operaton.bpm.engine.RuntimeService
import org.operaton.bpm.engine.TaskService

/**
 * Small extension helpers over the Operaton services so the adapters read as intent
 * ("correlate this message" / "complete this task") instead of fluent query boilerplate.
 * Everything here keys on the process business key, which this service sets to the application id.
 */

/** Correlates [messageName] to the single running instance carrying [businessKey]. */
fun RuntimeService.correlateByBusinessKey(messageName: String, businessKey: String) {
    createMessageCorrelation(messageName)
        .processInstanceBusinessKey(businessKey)
        .correlate()
}

/**
 * Completes the single open [taskDefinitionKey] task of the instance carrying [businessKey], passing
 * [variables]. Fails loudly when no such task is waiting.
 */
fun TaskService.completeTask(
    businessKey: String,
    taskDefinitionKey: String,
    variables: Map<String, Any> = emptyMap(),
) {
    val task =
        createTaskQuery()
            .processInstanceBusinessKey(businessKey)
            .taskDefinitionKey(taskDefinitionKey)
            .singleResult() ?: error("No open '$taskDefinitionKey' task for business key $businessKey")
    complete(task.id, variables)
}
