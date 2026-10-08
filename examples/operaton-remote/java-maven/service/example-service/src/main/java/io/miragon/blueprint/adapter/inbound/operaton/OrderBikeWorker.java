package io.miragon.blueprint.adapter.inbound.operaton;

import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.process.ServiceTasks;
import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.FlowNodes;
import java.util.HashMap;
import java.util.Map;
import org.operaton.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.operaton.bpm.client.task.ExternalTask;
import org.operaton.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;

@Component
@ExternalTaskSubscription(topicName = ServiceTasks.BIKE_LEASING_ORDER_BIKE)
public class OrderBikeWorker extends BaseExternalTaskWorker {

    private final OrderBikeUseCase useCase;

    public OrderBikeWorker(OrderBikeUseCase useCase) {
        this.useCase = useCase;
    }

    // Incident demo: the order task is its own external task (its own unit of work), so a dealer
    // "outage" for BIKE-FAIL fails this task specifically. Retry it 3 times, 10s apart, so the
    // countdown is visible in the Operaton Cockpit before an incident is raised on serviceTask_orderBike
    // (~30s) — the remote counterpart to a delegate's `failedJobRetryTimeCycle` R3/PT10S. This is the
    // only worker that retries; every other task keeps the base "fail fast, raise the incident" default.
    // Reproduce it with the 06-incident-demo Bruno collection.
    @Override
    protected int failureRetries() {
        return 3;
    }

    @Override
    protected long failureRetryTimeoutMs() {
        return 10_000L;
    }

    @Override
    protected void executeTask(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        OrderBikeUseCase.Result result = useCase.orderBike(ApplicationId.of(externalTask.getBusinessKey()));
        // Output variables the process routes on (`bikeAvailable`) and later reuses (`orderId`).
        Map<String, Object> variables = new HashMap<>();
        variables.put(
                FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.getValue(),
                result.orderId() != null ? result.orderId().value() : null);
        variables.put(FlowNodes.ServiceTaskOrderBike.Variables.BIKE_AVAILABLE.getValue(), result.bikeAvailable());
        externalTaskService.complete(externalTask, variables);
    }
}
