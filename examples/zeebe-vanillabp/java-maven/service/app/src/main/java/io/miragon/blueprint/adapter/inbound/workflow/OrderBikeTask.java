package io.miragon.blueprint.adapter.inbound.workflow;

import io.miragon.blueprint.adapter.process.Errors;
import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase;
import io.miragon.blueprint.domain.bike.BikeUnavailableException;
import io.vanillabp.spi.service.TaskException;
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
        try {
            aggregate.adopt(useCase.orderBike(aggregate.toDomain()));
        } catch (BikeUnavailableException unavailable) {
            throw new TaskException(unavailable.getMessage(), Errors.BIKE_UNAVAILABLE.getCode());
        }
    }
}
