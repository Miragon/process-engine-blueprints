package io.miragon.blueprint.process.util;

import org.operaton.bpm.engine.ExternalTaskService;
import org.operaton.bpm.engine.ProcessEngine;

/**
 * Process-test helper for the external tasks the workers consume.
 */
public final class WorkerUtils {

    private static final long TIMEOUT_MILLIS = 15_000;

    private WorkerUtils() {
    }

    /**
     * Waits until the real, asynchronously polling {@code @ProcessEngineWorker} beans have ended every open
     * external task (completed it or raised a BPMN error). It runs no job: the test executes the async
     * continuation that follows with its own {@code execute(job(...))}, because the job executor is disabled
     * in tests for determinism.
     */
    public static void awaitWorkers(ProcessEngine processEngine) {
        ExternalTaskService externalTaskService = processEngine.getExternalTaskService();
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (externalTaskService.createExternalTaskQuery().count() > 0) {
            if (System.currentTimeMillis() >= deadline) {
                throw new IllegalStateException("the workers left external tasks open for " + TIMEOUT_MILLIS + " ms");
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for the workers", e);
            }
        }
    }
}
