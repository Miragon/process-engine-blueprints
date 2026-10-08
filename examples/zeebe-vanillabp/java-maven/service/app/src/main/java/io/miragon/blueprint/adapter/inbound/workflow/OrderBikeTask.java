package io.miragon.blueprint.adapter.inbound.workflow;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase;
import io.vanillabp.spi.service.WorkflowTask;
import org.springframework.stereotype.Component;

@Component
public class OrderBikeTask extends BikeLeasingWorkflow {

    private final OrderBikeUseCase useCase;

    public OrderBikeTask(OrderBikeUseCase useCase) {
        this.useCase = useCase;
    }

    @WorkflowTask(taskDefinition = ServiceTasks.MIRAVELO_ORDER_BIKE)
    public void orderBike(LeasingApplicationAggregate aggregate) {
        aggregate.adopt(useCase.orderBike(aggregate.toDomain()));
    }
}
