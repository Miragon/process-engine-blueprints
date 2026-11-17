package io.miragon.blueprint.adapter.process;

import static org.assertj.core.api.Assertions.assertThat;

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes.StartEventLeasingRequestReceived;
import io.miragon.bpmn.runtime.BpmnErrorDefinition;
import io.miragon.bpmn.runtime.FlowNode;
import io.miragon.bpmn.runtime.MessageName;
import org.cibseven.bpm.engine.ProcessEngine;
import org.cibseven.bpm.engine.externaltask.ExternalTask;
import org.cibseven.bpm.engine.runtime.Job;
import org.cibseven.bpm.engine.runtime.ProcessInstance;
import org.cibseven.bpm.engine.task.Task;

import java.util.List;
import java.util.Map;

final class ProcessDriveUtils {

    /** Worker id the tests lock external tasks with — stands in for the remote worker. */
    private static final String TEST_WORKER = "test-worker";

    /** Lock duration for tests; irrelevant because tasks are completed immediately after locking. */
    private static final long TEST_LOCK_DURATION_MS = 60_000L;

    private ProcessDriveUtils() {
    }

    static void completeExternalTask(ProcessEngine engine, String topicName) {
        completeExternalTask(engine, topicName, Map.of());
    }

    /**
     * Completes the <b>single</b> waiting external task of {@code topicName}, as the remote worker would. It is
     * explicit about <i>which</i> task it drives and fails loudly if that task is not (uniquely) waiting, so a
     * test reads as an ordered trace. The {@code variables} are passed as the task's output — exactly what a
     * real worker would return (e.g. {@code orderBike} → {@code orderId}). It only ends the external task: the test runs the async
     * continuation that follows with its own {@code execute(job(...))}.
     */
    static void completeExternalTask(ProcessEngine engine, String topicName, Map<String, Object> variables) {
        ExternalTask task = lockSingleExternalTask(engine, topicName);
        engine.getExternalTaskService().complete(task.getId(), TEST_WORKER, variables);
    }

    /**
     * Ends the <b>single</b> waiting external task of {@code topicName} with the BPMN {@code error} — what a
     * real worker does through {@code handleBpmnError} — so the task is left through its error boundary event
     * instead of being completed. Like {@link #completeExternalTask(ProcessEngine, String, Map)}, it runs no
     * job.
     */
    static void raiseBpmnErrorOnExternalTask(ProcessEngine engine, String topicName, BpmnErrorDefinition error) {
        ExternalTask task = lockSingleExternalTask(engine, topicName);
        engine.getExternalTaskService().handleBpmnError(task.getId(), TEST_WORKER, error.getCode());
    }

    private static ExternalTask lockSingleExternalTask(ProcessEngine engine, String topicName) {
        List<ExternalTask> tasks =
                engine.getExternalTaskService().createExternalTaskQuery().topicName(topicName).notLocked().list();
        if (tasks.size() != 1) {
            throw new IllegalArgumentException(
                    "expected exactly one waiting external task for topic '" + topicName + "', found " + tasks.size());
        }
        ExternalTask task = tasks.get(0);
        engine.getExternalTaskService().lock(task.getId(), TEST_WORKER, TEST_LOCK_DURATION_MS);
        return task;
    }

    static void completeUserTask(ProcessEngine engine, String taskDefinitionKey) {
        completeUserTask(engine, taskDefinitionKey, Map.of());
    }

    /** Completes the single waiting user task with {@code taskDefinitionKey}. */
    static void completeUserTask(ProcessEngine engine, String taskDefinitionKey, Map<String, Object> variables) {
        Task task = engine.getTaskService().createTaskQuery().taskDefinitionKey(taskDefinitionKey).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("no waiting user task '" + taskDefinitionKey + "'");
        }
        engine.getTaskService().complete(task.getId(), variables);
    }

    /** Correlates {@code message} to the instance identified by {@code businessKey}. */
    static void correlateMessage(ProcessEngine engine, MessageName message, String businessKey) {
        engine.getRuntimeService()
                .createMessageCorrelation(message.getValue())
                .processInstanceBusinessKey(businessKey)
                .correlate();
    }

    /**
     * Fires the timer job of the given boundary/catch event directly, regardless of its due date — the
     * tests verify the timer path is wired correctly, not the real-world waiting duration.
     */
    static void fireTimer(ProcessEngine engine, FlowNode timerEvent) {
        Job timer = engine.getManagementService()
                .createJobQuery()
                .timers()
                .activityId(timerEvent.getId().getValue())
                .singleResult();
        if (timer == null) {
            throw new IllegalArgumentException(
                    "no timer job found for activity '" + timerEvent.getId().getValue() + "'");
        }
        engine.getManagementService().executeJob(timer.getId());
    }

    /** Starts the process through its message start event, keyed by {@code businessKey}. */
    static void startLeasing(ProcessEngine engine, String businessKey, int age, double income, String bikeId) {
        engine.getRuntimeService().startProcessInstanceByMessage(
                Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.getValue(),
                businessKey,
                Map.of(
                        StartEventLeasingRequestReceived.Variables.APPLICATION_ID.getValue(), businessKey,
                        StartEventLeasingRequestReceived.Variables.BIKE_ID.getValue(), bikeId,
                        StartEventLeasingRequestReceived.Variables.MONTHLY_NET_INCOME.getValue(), income,
                        StartEventLeasingRequestReceived.Variables.AGE.getValue(), age));
    }

    /** Finds the bike-leasing process instance for the given business key. Fails the test if none exists. */
    static ProcessInstance findInstance(ProcessEngine engine, String businessKey) {
        ProcessInstance instance = engine.getRuntimeService()
                .createProcessInstanceQuery()
                .processDefinitionKey(BikeLeasingProcessProcessApi.PROCESS_ID.getValue())
                .processInstanceBusinessKey(businessKey)
                .singleResult();
        assertThat(instance).as("process instance for business key %s", businessKey).isNotNull();
        return instance;
    }
}
