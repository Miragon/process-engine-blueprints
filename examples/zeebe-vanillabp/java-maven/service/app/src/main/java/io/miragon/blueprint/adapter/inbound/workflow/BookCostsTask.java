package io.miragon.blueprint.adapter.inbound.workflow;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase;
import io.vanillabp.spi.service.WorkflowTask;
import org.springframework.stereotype.Component;

@Component
public class BookCostsTask extends BikeLeasingWorkflow {

    private final BookCancellationCostsUseCase useCase;

    public BookCostsTask(BookCancellationCostsUseCase useCase) {
        this.useCase = useCase;
    }

    @WorkflowTask(taskDefinition = ServiceTasks.MIRAVELO_BOOK_COSTS)
    public void bookCosts(LeasingApplicationAggregate aggregate) {
        useCase.bookCosts(aggregate.placedOrderId());
    }
}
