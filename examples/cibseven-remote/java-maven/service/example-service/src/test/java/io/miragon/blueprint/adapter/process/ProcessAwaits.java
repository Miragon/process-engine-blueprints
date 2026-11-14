package io.miragon.blueprint.adapter.process;

import static org.awaitility.Awaitility.await;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;

import io.miragon.bpmn.runtime.FlowNode;
import java.time.Duration;
import org.awaitility.core.ThrowingRunnable;
import org.cibseven.bpm.engine.ProcessEngine;
import org.cibseven.bpm.engine.runtime.Job;
import org.cibseven.bpm.engine.runtime.ProcessInstance;
import org.cibseven.bpm.engine.task.Task;

final class ProcessAwaits {

    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(15);

    private ProcessAwaits() {
    }

    static void awaitWaitingAt(ProcessInstance instance, FlowNode node) {
        awaitAsserted(() -> assertThat(instance).isWaitingAt(node.getId().getValue()));
    }

    static void awaitPassed(ProcessInstance instance, FlowNode node) {
        awaitAsserted(() -> assertThat(instance).hasPassed(node.getId().getValue()));
    }

    static void awaitEnded(ProcessInstance instance) {
        awaitAsserted(() -> assertThat(instance).isEnded());
    }

    static void awaitUserTask(ProcessEngine engine, String taskKey) {
        awaitAsserted(() -> assertThat(userTask(engine, taskKey)).isNotNull());
    }

    /** Fires the timer of {@code timerEvent} now and leaves what follows to the job executor. */
    static void fireTimerAndContinueInBackground(ProcessEngine engine, FlowNode timerEvent) {
        engine.getManagementService().executeJob(timerJobOf(engine, timerEvent).getId());
    }

    private static void awaitAsserted(ThrowingRunnable assertion) {
        await().atMost(PROCESS_TIMEOUT).untilAsserted(assertion);
    }

    private static Task userTask(ProcessEngine engine, String taskKey) {
        return engine.getTaskService().createTaskQuery().taskDefinitionKey(taskKey).singleResult();
    }

    private static Job timerJobOf(ProcessEngine engine, FlowNode timerEvent) {
        String activityId = timerEvent.getId().getValue();
        Job timer = engine.getManagementService().createJobQuery().timers().activityId(activityId).singleResult();
        if (timer == null) {
            throw new IllegalStateException("no timer job of '" + activityId + "'");
        }
        return timer;
    }
}
