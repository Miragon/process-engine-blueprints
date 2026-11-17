package io.miragon.blueprint.adapter.process;

import static org.assertj.core.api.Assertions.assertThat;

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes;
import io.miragon.bpmn.runtime.BpmnErrorDefinition;
import io.miragon.bpmn.runtime.FlowNode;
import io.miragon.bpmn.runtime.MessageName;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.externaltask.ExternalTask;
import org.operaton.bpm.engine.runtime.Job;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.operaton.bpm.engine.task.Task;

/** Static helpers that drive a running {@link ProcessEngine} through the bike-leasing process in tests. */
public final class ProcessDriveUtils {

    /** Worker id the tests lock external tasks with — stands in for the remote worker. */
    private static final String TEST_WORKER = "test-worker";

    /** Lock duration for tests; irrelevant because tasks are completed immediately after locking. */
    private static final long TEST_LOCK_DURATION_MS = 60_000L;

    private ProcessDriveUtils() {
    }

    /**
     * Completes the <strong>single</strong> waiting external task of {@code topicName}, as the remote
     * worker would. It is explicit about <em>which</em> task it drives and fails loudly if that task is not
     * (uniquely) waiting, so a test reads as an ordered trace. The {@code variables} are passed as the
     * task's output — exactly what a real worker would return (e.g. {@code orderBike} → {@code orderId}).
     * It only ends the external task: the test runs the async continuation that follows with its own
     * {@code execute(job(...))}.
     */
    public static void completeExternalTask(ProcessEngine engine, String topicName, Map<String, Object> variables) {
        ExternalTask task = lockSingleExternalTask(engine, topicName);
        engine.getExternalTaskService().complete(task.getId(), TEST_WORKER, variables);
    }

    /**
     * Ends the <strong>single</strong> waiting external task of {@code topicName} with the BPMN
     * {@code error} — what a real worker does through {@code handleBpmnError} — so the task is left
     * through its error boundary event instead of being completed. Like
     * {@link #completeExternalTask(ProcessEngine, String, Map)}, it runs no job.
     */
    public static void raiseBpmnErrorOnExternalTask(
            ProcessEngine engine, String topicName, BpmnErrorDefinition error) {
        ExternalTask task = lockSingleExternalTask(engine, topicName);
        engine.getExternalTaskService().handleBpmnError(task.getId(), TEST_WORKER, error.getCode());
    }

    private static ExternalTask lockSingleExternalTask(ProcessEngine engine, String topicName) {
        List<ExternalTask> tasks = engine.getExternalTaskService().createExternalTaskQuery()
                .topicName(topicName).notLocked().list();
        if (tasks.size() != 1) {
            throw new IllegalArgumentException(
                    "expected exactly one waiting external task for topic '" + topicName
                            + "', found " + tasks.size());
        }
        ExternalTask task = tasks.get(0);
        engine.getExternalTaskService().lock(task.getId(), TEST_WORKER, TEST_LOCK_DURATION_MS);
        return task;
    }

    public static void completeExternalTask(ProcessEngine engine, String topicName) {
        completeExternalTask(engine, topicName, Map.of());
    }

    /** Completes the single waiting user task with {@code taskDefinitionKey}. */
    public static void completeUserTask(ProcessEngine engine, String taskDefinitionKey, Map<String, Object> variables) {
        Task task = engine.getTaskService().createTaskQuery().taskDefinitionKey(taskDefinitionKey).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("no waiting user task '" + taskDefinitionKey + "'");
        }
        engine.getTaskService().complete(task.getId(), variables);
    }

    public static void completeUserTask(ProcessEngine engine, String taskDefinitionKey) {
        completeUserTask(engine, taskDefinitionKey, Map.of());
    }

    /** Correlates {@code message} to the instance identified by {@code businessKey}. */
    public static void correlateMessage(ProcessEngine engine, MessageName message, String businessKey) {
        engine.getRuntimeService().createMessageCorrelation(message.getValue())
                .processInstanceBusinessKey(businessKey).correlate();
    }

    /**
     * Fires the timer job of the given boundary/catch event directly, regardless of its due date — the
     * tests verify the timer path is wired correctly, not the real-world waiting duration.
     */
    public static void fireTimer(ProcessEngine engine, FlowNode timerEvent) {
        Job timer = engine.getManagementService().createJobQuery().timers()
                .activityId(timerEvent.getId().getValue()).singleResult();
        if (timer == null) {
            throw new IllegalArgumentException(
                    "no timer job found for activity '" + timerEvent.getId().getValue() + "'");
        }
        engine.getManagementService().executeJob(timer.getId());
    }

    /** Starts the process through its message start event, keyed by {@code businessKey}. */
    public static void startLeasing(ProcessEngine engine, String businessKey, int age, double income, String bikeId) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put(FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID.getValue(), businessKey);
        variables.put(FlowNodes.StartEventLeasingRequestReceived.Variables.BIKE_ID.getValue(), bikeId);
        variables.put(FlowNodes.StartEventLeasingRequestReceived.Variables.MONTHLY_NET_INCOME.getValue(), income);
        variables.put(FlowNodes.StartEventLeasingRequestReceived.Variables.AGE.getValue(), age);
        engine.getRuntimeService().startProcessInstanceByMessage(
                Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.getValue(),
                businessKey,
                variables);
    }

    /** Finds the bike-leasing process instance for the given business key. Fails the test if none exists. */
    public static ProcessInstance findInstance(ProcessEngine engine, String businessKey) {
        ProcessInstance instance = engine.getRuntimeService().createProcessInstanceQuery()
                .processDefinitionKey(BikeLeasingProcessProcessApi.PROCESS_ID.getValue())
                .processInstanceBusinessKey(businessKey)
                .singleResult();
        assertThat(instance).as("process instance for business key %s", businessKey).isNotNull();
        return instance;
    }
}
