package io.miragon.blueprint.adapter.inbound.workflow;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase;
import io.vanillabp.spi.service.WorkflowTask;
import org.springframework.stereotype.Component;

@Component
public class RequestCancellationTask extends BikeLeasingWorkflow {

    private final RequestOrderCancellationUseCase useCase;

    public RequestCancellationTask(RequestOrderCancellationUseCase useCase) {
        this.useCase = useCase;
    }

    @WorkflowTask(taskDefinition = ServiceTasks.MIRAVELO_REQUEST_CANCELLATION)
    public void requestCancellation(LeasingApplicationAggregate aggregate) {
        aggregate.setCancellationPossible(useCase.requestCancellation(aggregate.placedOrderId()));
    }
}
