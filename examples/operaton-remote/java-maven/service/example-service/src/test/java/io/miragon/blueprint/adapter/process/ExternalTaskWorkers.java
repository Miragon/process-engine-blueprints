package io.miragon.blueprint.adapter.process;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.operaton.bpm.client.ExternalTaskClient;
import org.operaton.bpm.client.spring.SpringTopicSubscription;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.externaltask.ExternalTask;
import org.operaton.bpm.engine.externaltask.ExternalTaskQuery;
import org.springframework.boot.test.context.TestComponent;

/**
 * Runs this service's real external-task workers one task at a time — the remote counterpart to the
 * embedded blueprint's {@code executeJobFor(activity)}. It polls with the worker's own Spring subscription until the
 * worker has handled the waiting task over {@code /engine-rest}, then runs the engine's async continuations.
 */
@TestComponent
class ExternalTaskWorkers {

    private static final Duration WORKER_TIMEOUT = Duration.ofSeconds(10);

    private final ProcessEngine engine;
    private final ExternalTaskClient client;
    private final Map<String, SpringTopicSubscription> subscriptionByTopic;

    ExternalTaskWorkers(ProcessEngine engine, ExternalTaskClient client, List<SpringTopicSubscription> subscriptions) {
        this.engine = engine;
        this.client = client;
        this.subscriptionByTopic = subscriptions.stream().collect(toMap(SpringTopicSubscription::getTopicName, identity()));
    }

    void execute(String topicName) {
        runWorkerOnWaitingTask(topicName);
        ProcessDriveUtils.executeAsyncContinuations(engine);
    }

    /** Lets the workers handle the waiting tasks in engine order — for chains such as compensation. */
    void drainToWaitState() {
        ProcessDriveUtils.executeAsyncContinuations(engine);
        for (Optional<String> topic = nextWaitingTopic(); topic.isPresent(); topic = nextWaitingTopic()) {
            execute(topic.get());
        }
    }

    private void runWorkerOnWaitingTask(String topicName) {
        String taskId = singleWaitingTaskId(topicName);
        SpringTopicSubscription subscription = subscriptionOf(topicName);
        startPolling(subscription);
        try {
            await().atMost(WORKER_TIMEOUT).until(() -> isHandled(taskId));
        } finally {
            stopPolling(subscription);
        }
    }

    private void startPolling(SpringTopicSubscription subscription) {
        subscription.open();
        client.start();
    }

    /** Stopping waits for the poll in flight, so no later task is fetched behind the test's back. */
    private void stopPolling(SpringTopicSubscription subscription) {
        client.stop();
        subscription.close();
    }

    private boolean isHandled(String taskId) {
        Optional<ExternalTask> openTask = Optional.ofNullable(externalTasks().externalTaskId(taskId).singleResult());
        openTask.ifPresent(ExternalTaskWorkers::failIfWorkerFailed);
        return openTask.isEmpty();
    }

    private static void failIfWorkerFailed(ExternalTask task) {
        if (task.getErrorMessage() != null) {
            throw new IllegalStateException("worker of '" + task.getTopicName() + "' failed: " + task.getErrorMessage());
        }
    }

    private String singleWaitingTaskId(String topicName) {
        List<ExternalTask> waitingTasks = externalTasks().topicName(topicName).notLocked().list();
        if (waitingTasks.size() != 1) {
            throw new IllegalStateException("expected one waiting task of '" + topicName + "', found " + waitingTasks.size());
        }
        return waitingTasks.get(0).getId();
    }

    private Optional<String> nextWaitingTopic() {
        return externalTasks().notLocked().listPage(0, 1).stream().findFirst().map(ExternalTask::getTopicName);
    }

    private SpringTopicSubscription subscriptionOf(String topicName) {
        SpringTopicSubscription subscription = subscriptionByTopic.get(topicName);
        if (subscription == null) {
            throw new IllegalStateException("no worker subscribes to '" + topicName + "'");
        }
        return subscription;
    }

    private ExternalTaskQuery externalTasks() {
        return engine.getExternalTaskService().createExternalTaskQuery();
    }
}
