package io.miragon.blueprint.adapter.outbound.zeebe;

import dev.bpmcrafters.processengineapi.CommonRestrictions;
import dev.bpmcrafters.processengineapi.correlation.CorrelateMessageCmd;
import dev.bpmcrafters.processengineapi.correlation.Correlation;
import dev.bpmcrafters.processengineapi.correlation.CorrelationApi;
import dev.bpmcrafters.processengineapi.process.StartProcessApi;
import dev.bpmcrafters.processengineapi.process.StartProcessByMessageCmd;
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd;
import dev.bpmcrafters.processengineapi.task.TaskInformation;
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi;
import dev.bpmcrafters.processengineapi.task.support.UserTaskSupport;
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes;
import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi;
import io.miragon.blueprint.adapter.process.Messages;
import io.miragon.blueprint.application.port.outbound.LeasingProcess;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Drives the Camunda 8 / Zeebe engine through the process-engine-api. Zeebe has no business key, so the
 * application id travels as a process variable and is declared as the message subscription correlation
 * key on every catch event; messages are published with that id as their correlation key. The variable
 * and element names come from the typed process API generated from {@code bike-leasing.bpmn}.
 *
 * <p>The user tasks are looked up in the {@link UserTaskSupport} pool (fed by the adapter's user-task
 * delivery) and then completed via the {@link UserTaskCompletionApi} — the
 * process-engine-api has no server-side task query, so completion always goes through a resolved task id.
 */
@Component
public class LeasingProcessAdapter implements LeasingProcess {

    private static final int TASK_LOOKUP_ATTEMPTS = 50;
    private static final long TASK_LOOKUP_INTERVAL_MS = 200L;

    private final StartProcessApi startProcessApi;
    private final CorrelationApi correlationApi;
    private final UserTaskSupport userTaskSupport;
    private final UserTaskCompletionApi userTaskCompletionApi;

    public LeasingProcessAdapter(
            StartProcessApi startProcessApi,
            CorrelationApi correlationApi,
            UserTaskSupport userTaskSupport,
            UserTaskCompletionApi userTaskCompletionApi) {
        this.startProcessApi = startProcessApi;
        this.correlationApi = correlationApi;
        this.userTaskSupport = userTaskSupport;
        this.userTaskCompletionApi = userTaskCompletionApi;
    }

    @Override
    public void submitRequest(LeasingApplication application) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(
                FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID.getValue(),
                application.id().value().toString());
        payload.put(FlowNodes.StartEventLeasingRequestReceived.Variables.BIKE_ID.getValue(), application.bikeId().value());
        payload.put(
                FlowNodes.StartEventLeasingRequestReceived.Variables.MONTHLY_NET_INCOME.getValue(),
                application.monthlyNetIncome());
        payload.put(FlowNodes.StartEventLeasingRequestReceived.Variables.AGE.getValue(), application.age());
        // Publishing the leasing-request message starts a new instance via the message start event.
        startProcessApi.startProcess(
                new StartProcessByMessageCmd(Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.getValue(), payload))
                .join();
    }

    @Override
    public void correlateContractSigned(ApplicationId id) {
        correlate(Messages.MIRAVELO_CONTRACT_SIGNED.getValue(), id);
    }

    @Override
    public void correlateHandoverReported(ApplicationId id) {
        correlate(Messages.MIRAVELO_HANDOVER_REPORTED.getValue(), id);
    }

    @Override
    public void correlateApplicationWithdrawn(ApplicationId id) {
        correlate(Messages.MIRAVELO_APPLICATION_WITHDRAWN.getValue(), id);
    }

    private void correlate(String messageName, ApplicationId id) {
        correlationApi.correlateMessage(
                new CorrelateMessageCmd(
                        messageName,
                        Map.of(),
                        Correlation.withKey(id.value().toString()),
                        Map.of()))
                .join();
    }

    @Override
    public void completeAlternativeClarification(ApplicationId id, boolean alternativeFound, BikeId bikeId) {
        String taskId = awaitTaskId(id, FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put(FlowNodes.UserTaskClarifyAlternative.Variables.ALTERNATIVE_FOUND.getValue(), alternativeFound);
        if (bikeId != null) {
            variables.put(FlowNodes.UserTaskClarifyAlternative.Variables.BIKE_ID.getValue(), bikeId.value());
        }
        userTaskCompletionApi.completeTask(new CompleteTaskCmd(taskId, variables)).join();
    }

    @Override
    public void completeReturnClarification(ApplicationId id) {
        String taskId = awaitTaskId(id, CancelBikeOrderProcessApi.FlowNodes.UserTaskClarifyReturn.ELEMENT_ID);
        userTaskCompletionApi.completeTask(new CompleteTaskCmd(taskId, Map.of())).join();
    }

    /**
     * Resolves the open {@code taskDefinitionKey} task id for {@code id} from the {@link UserTaskSupport}
     * pool, waiting briefly for the adapter's user-task delivery to hand it over. Fails with
     * {@link NoSuchElementException}, which the REST advice maps to a 409: the token is not at this task.
     */
    private String awaitTaskId(ApplicationId id, String taskDefinitionKey) {
        String applicationId = id.value().toString();
        String applicationIdKey = FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID.getValue();
        for (int attempt = 0; attempt < TASK_LOOKUP_ATTEMPTS; attempt++) {
            Optional<TaskInformation> task = userTaskSupport.getAllTasks().stream()
                    .filter(it -> taskDefinitionKey.equals(it.getMeta().get(CommonRestrictions.ACTIVITY_ID)))
                    .filter(it -> hasApplicationId(it, applicationIdKey, applicationId))
                    .findFirst();
            if (task.isPresent()) {
                return task.get().getTaskId();
            }
            try {
                Thread.sleep(TASK_LOOKUP_INTERVAL_MS);
            } catch (InterruptedException e) {
                // Keep the interrupt visible and fail with an unchecked exception, which also rolls back the
                // surrounding @Transactional use case; Spring's default rule would commit a checked one.
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for the '" + taskDefinitionKey + "' task", e);
            }
        }
        throw new NoSuchElementException(
                "No active '" + taskDefinitionKey + "' task for application " + applicationId);
    }

    /** Whether the pooled task's payload carries the application id; a failing payload lookup is no match. */
    private boolean hasApplicationId(TaskInformation task, String applicationIdKey, String applicationId) {
        try {
            return Objects.equals(userTaskSupport.getPayload(task.getTaskId()).get(applicationIdKey), applicationId);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
