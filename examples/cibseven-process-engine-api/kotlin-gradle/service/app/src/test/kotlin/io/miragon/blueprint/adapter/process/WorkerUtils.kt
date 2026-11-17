package io.miragon.blueprint.adapter.process

import org.cibseven.bpm.engine.ProcessEngine

private const val TIMEOUT_MILLIS = 15_000L

/**
 * Waits until the real, asynchronously polling `@ProcessEngineWorker` beans have ended every open
 * external task (completed it or raised a BPMN error). It runs no job: the test executes the async
 * continuation that follows with its own `execute(job(...))`, because the job executor is disabled in
 * tests for determinism.
 */
fun ProcessEngine.awaitWorkers() {
    val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
    while (externalTaskService.createExternalTaskQuery().count() > 0) {
        check(System.currentTimeMillis() < deadline) { "the workers left external tasks open for $TIMEOUT_MILLIS ms" }
        Thread.sleep(100)
    }
}
