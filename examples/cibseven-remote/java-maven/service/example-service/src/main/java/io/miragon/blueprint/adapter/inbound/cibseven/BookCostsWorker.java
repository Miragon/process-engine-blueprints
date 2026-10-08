package io.miragon.blueprint.adapter.inbound.cibseven;

import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.process.CancelBikeOrderProcessApi.FlowNodes;
import io.miragon.blueprint.process.ServiceTasks;
import org.cibseven.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.cibseven.bpm.client.task.ExternalTask;
import org.cibseven.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;

@Component
@ExternalTaskSubscription(topicName = ServiceTasks.BIKE_LEASING_BOOK_COSTS)
public class BookCostsWorker extends BaseExternalTaskWorker {

    private final BookCancellationCostsUseCase useCase;

    public BookCostsWorker(BookCancellationCostsUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void executeTask(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        // `orderId` is handed to the cancelBikeOrder sub-process by the calling activity.
        OrderId orderId = new OrderId(externalTask.getVariable(FlowNodes.StartEventCancellationRequired.Variables.ORDER_ID.getValue()));
        useCase.bookCosts(orderId);
        externalTaskService.complete(externalTask);
    }
}
