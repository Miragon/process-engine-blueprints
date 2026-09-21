package io.miragon.blueprint.adapter.inbound.operaton;

import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.process.CancelBikeOrderProcessApi.ServiceTasks;
import io.miragon.blueprint.process.CancelBikeOrderProcessApi.Variables;
import org.operaton.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.operaton.bpm.client.task.ExternalTask;
import org.operaton.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;

@Component
@ExternalTaskSubscription(topicName = ServiceTasks.BIKE_LEASING_BOOK_COSTS)
public class BookCostsWorker extends BaseExternalTaskWorker {

    private final BookCancellationCostsUseCase useCase;

    public BookCostsWorker(BookCancellationCostsUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    protected void executeTask(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        // `orderId` is handed to the cancelBikeOrder sub-process by the calling activity.
        OrderId orderId = new OrderId(externalTask.getVariable(Variables.StartEventCancellationRequired.ORDER_ID.getValue()));
        useCase.bookCosts(orderId);
        externalTaskService.complete(externalTask);
    }
}
