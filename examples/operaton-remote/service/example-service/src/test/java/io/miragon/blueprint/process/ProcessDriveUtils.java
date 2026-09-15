package io.miragon.blueprint.process;

import static org.assertj.core.api.Assertions.assertThat;

import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.Messages;
import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.Variables;
import io.miragon.bpmn.runtime.ElementId;
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
     * Completes the <strong>single</strong> waiting external task of {@code topicName} — the remote
     * counterpart to the embedded blueprint's {@code executeJobFor(activityId)}. It is explicit about
     * <em>which</em> task it drives and fails loudly if that task is not (uniquely) waiting, so a test
     * reads as an ordered trace. The {@code variables} are passed as the task's output — exactly what a
     * real worker would return (e.g. {@code orderBike} → {@code bikeAvailable} / {@code orderId}).
     * Afterwards it settles the deterministic async plumbing (asyncAfter jobs, the DMN, gateways) up to
     * the next wait state or external task.
     */
    public static void completeExternalTask(ProcessEngine engine, String topicName, Map<String, Object> variables) {
        List<ExternalTask> tasks = engine.getExternalTaskService().createExternalTaskQuery()
                .topicName(topicName).notLocked().list();
        if (tasks.size() != 1) {
            throw new IllegalArgumentException(
                    "expected exactly one waiting external task for topic '" + topicName
                            + "', found " + tasks.size());
        }
        ExternalTask task = tasks.get(0);
        engine.getExternalTaskService().lock(task.getId(), TEST_WORKER, TEST_LOCK_DURATION_MS);
        engine.getExternalTaskService().complete(task.getId(), TEST_WORKER, variables);
        executeAsyncContinuations(engine);
    }

    public static void completeExternalTask(ProcessEngine engine, String topicName) {
        completeExternalTask(engine, topicName, Map.of());
    }

    /** Completes the single waiting user task with {@code taskDefinitionKey}, then settles the plumbing. */
    public static void completeUserTask(ProcessEngine engine, String taskDefinitionKey, Map<String, Object> variables) {
        Task task = engine.getTaskService().createTaskQuery().taskDefinitionKey(taskDefinitionKey).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("no waiting user task '" + taskDefinitionKey + "'");
        }
        engine.getTaskService().complete(task.getId(), variables);
        executeAsyncContinuations(engine);
    }

    public static void completeUserTask(ProcessEngine engine, String taskDefinitionKey) {
        completeUserTask(engine, taskDefinitionKey, Map.of());
    }

    /** Correlates {@code message} to the instance identified by {@code businessKey}, then settles the plumbing. */
    public static void correlateMessage(ProcessEngine engine, MessageName message, String businessKey) {
        engine.getRuntimeService().createMessageCorrelation(message.getValue())
                .processInstanceBusinessKey(businessKey).correlate();
        executeAsyncContinuations(engine);
    }

    /**
     * Fires the timer job of the given boundary/catch event directly, regardless of its due date — the
     * tests verify the timer path is wired correctly, not the real-world waiting duration.
     */
    public static void fireTimer(ProcessEngine engine, ElementId timerActivityId) {
        Job timer = engine.getManagementService().createJobQuery().timers()
                .activityId(timerActivityId.getValue()).singleResult();
        if (timer == null) {
            throw new IllegalArgumentException(
                    "no timer job found for activity '" + timerActivityId.getValue() + "'");
        }
        engine.getManagementService().executeJob(timer.getId());
        executeAsyncContinuations(engine);
    }

    /**
     * Generic fallback for <strong>engine-ordered</strong> chains — e.g. compensation, whose handlers run
     * in an implementation-defined order where enumerating each external task would be brittle. It drives
     * async jobs <em>and</em> completes whatever external tasks are waiting (with the per-topic
     * {@code externalTaskOutputs}) until the next wait state. Prefer {@link #completeExternalTask} for the
     * deterministic, linear parts of a flow.
     */
    public static void drainToWaitState(
            ProcessEngine engine,
            Map<String, Map<String, Object>> externalTaskOutputs,
            int maxIterations) {
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            Job job = engine.getManagementService().createJobQuery().active().messages().listPage(0, 1)
                    .stream().findFirst().orElse(null);
            if (job != null) {
                engine.getManagementService().executeJob(job.getId());
                continue;
            }
            List<ExternalTask> tasks = engine.getExternalTaskService().createExternalTaskQuery()
                    .notLocked().listPage(0, 1);
            if (tasks.isEmpty()) {
                return;
            }
            ExternalTask task = tasks.get(0);
            engine.getExternalTaskService().lock(task.getId(), TEST_WORKER, TEST_LOCK_DURATION_MS);
            Map<String, Object> output = externalTaskOutputs.get(task.getTopicName());
            engine.getExternalTaskService().complete(
                    task.getId(), TEST_WORKER, output != null ? output : Map.of());
        }
        throw new IllegalStateException(
                "process did not reach a wait state within " + maxIterations + " iterations");
    }

    public static void drainToWaitState(ProcessEngine engine, Map<String, Map<String, Object>> externalTaskOutputs) {
        drainToWaitState(engine, externalTaskOutputs, 100);
    }

    /** Starts the process through its message start event, keyed by {@code businessKey}, then settles the plumbing. */
    public static void startLeasing(ProcessEngine engine, String businessKey, int age, double income, String bikeId) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put(Variables.StartEventLeasingRequestReceived.APPLICATION_ID.getValue(), businessKey);
        variables.put(Variables.StartEventLeasingRequestReceived.BIKE_ID.getValue(), bikeId);
        variables.put(Variables.StartEventLeasingRequestReceived.MONTHLY_NET_INCOME.getValue(), income);
        variables.put(Variables.StartEventLeasingRequestReceived.AGE.getValue(), age);
        engine.getRuntimeService().startProcessInstanceByMessage(
                Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.getValue(),
                businessKey,
                variables);
        executeAsyncContinuations(engine);
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

    /**
     * Drives all pending async-continuation jobs (start event, DMN, gateways, {@code asyncAfter}) until the
     * process settles at its next wait state or external task. Timer jobs are excluded — those are fired
     * explicitly via {@link #fireTimer}.
     */
    private static void executeAsyncContinuations(ProcessEngine engine) {
        executeAsyncContinuations(engine, 100);
    }

    private static void executeAsyncContinuations(ProcessEngine engine, int maxIterations) {
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            Job job = engine.getManagementService().createJobQuery().active().messages().listPage(0, 1)
                    .stream().findFirst().orElse(null);
            if (job == null) {
                return;
            }
            engine.getManagementService().executeJob(job.getId());
        }
        throw new IllegalStateException(
                "async continuations did not settle within " + maxIterations + " iterations");
    }
}
