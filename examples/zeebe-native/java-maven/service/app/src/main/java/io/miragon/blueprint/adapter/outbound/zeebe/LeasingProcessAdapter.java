package io.miragon.blueprint.adapter.outbound.zeebe;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Variable;
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes;
import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi;
import io.miragon.blueprint.adapter.process.Messages;
import io.miragon.blueprint.application.port.outbound.LeasingProcess;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.bpmn.runtime.VariableName;
import io.miragon.common.zeebe.engine.ProcessEngineApi;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Drives the Camunda 8 / Zeebe engine. Zeebe has no business key, so the application id travels as a
 * process variable and is declared as the message subscription correlation key on every catch event;
 * messages are published with that id as their correlation key, and a user task is looked up by its
 * element id plus the instance's {@code applicationId} variable. The variable and element names come from
 * the typed process APIs generated from {@code bike-leasing.bpmn} and {@code cancel-bike-order.bpmn}.
 */
@Component
public class LeasingProcessAdapter implements LeasingProcess {

    private final ProcessEngineApi engineApi;

    private final CamundaClient camundaClient;

    public LeasingProcessAdapter(ProcessEngineApi engineApi, CamundaClient camundaClient) {
        this.engineApi = engineApi;
        this.camundaClient = camundaClient;
    }

    @Override
    public void submitRequest(LeasingApplication application) {
        Map<VariableName, Object> variables = new LinkedHashMap<>();
        variables.put(FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID, application.id().value().toString());
        variables.put(FlowNodes.StartEventLeasingRequestReceived.Variables.BIKE_ID, application.bikeId().value());
        variables.put(FlowNodes.StartEventLeasingRequestReceived.Variables.MONTHLY_NET_INCOME, application.monthlyNetIncome());
        variables.put(FlowNodes.StartEventLeasingRequestReceived.Variables.AGE, application.age());
        // Publishing the leasing-request message starts a new instance via the message start event.
        engineApi.sendMessage(Messages.MIRAVELO_LEASING_REQUEST_RECEIVED, application.id().value().toString(), variables);
    }

    @Override
    public void correlateContractSigned(ApplicationId id) {
        engineApi.sendMessage(Messages.MIRAVELO_CONTRACT_SIGNED, id.value().toString());
    }

    @Override
    public void correlateHandoverReported(ApplicationId id) {
        engineApi.sendMessage(Messages.MIRAVELO_HANDOVER_REPORTED, id.value().toString());
    }

    @Override
    public void correlateApplicationWithdrawn(ApplicationId id) {
        engineApi.sendMessage(Messages.MIRAVELO_APPLICATION_WITHDRAWN, id.value().toString());
    }

    @Override
    public void completeAlternativeClarification(ApplicationId id, boolean alternativeFound, BikeId bikeId) {
        long userTaskKey = findActiveUserTask(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID, id);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put(FlowNodes.UserTaskClarifyAlternative.Variables.ALTERNATIVE_FOUND.getValue(), alternativeFound);
        if (bikeId != null) {
            variables.put(FlowNodes.UserTaskClarifyAlternative.Variables.BIKE_ID.getValue(), bikeId.value());
        }
        camundaClient.newCompleteUserTaskCommand(userTaskKey).variables(variables).send().join();
    }

    @Override
    public void completeReturnClarification(ApplicationId id) {
        long userTaskKey = findActiveUserTask(CancelBikeOrderProcessApi.FlowNodes.UserTaskClarifyReturn.ELEMENT_ID, id);
        camundaClient.newCompleteUserTaskCommand(userTaskKey).send().join();
    }

    private long findActiveUserTask(String elementId, ApplicationId id) {
        // Match by reading each open task's applicationId variable rather than filtering the search by
        // process-instance variables: on an Elasticsearch-backed cluster that variable filter is not
        // reliably in sync with the task index (the task is searchable before the filter matches), so
        // it would spuriously find nothing. The per-task variable read is the same path the task inbox
        // uses and stays consistent with the task's own visibility.
        List<UserTask> openTasks = camundaClient.newUserTaskSearchRequest()
            .filter(filter -> {
                filter.state(UserTaskState.CREATED);
                filter.elementId(elementId);
            })
            .send()
            .join()
            .items();
        String applicationId = id.value().toString();
        return openTasks.stream()
            .filter(task -> readApplicationId(task.getUserTaskKey()).filter(applicationId::equals).isPresent())
            .findFirst()
            .map(UserTask::getUserTaskKey)
            .orElseThrow(() -> new NoSuchElementException(
                "No active '" + elementId + "' task for application " + id.value()));
    }

    /** Zeebe returns variable values as JSON, so a string comes back quoted — strip the quotes. */
    private Optional<String> readApplicationId(long userTaskKey) {
        String applicationIdVariable = FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID.getValue();
        return camundaClient.newUserTaskVariableSearchRequest(userTaskKey)
            .send()
            .join()
            .items()
            .stream()
            .filter(variable -> applicationIdVariable.equals(variable.getName()))
            .findFirst()
            .map(Variable::getValue)
            .map(LeasingProcessAdapter::removeSurroundingQuotes);
    }

    private static String removeSurroundingQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
