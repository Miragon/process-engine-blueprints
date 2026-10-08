package io.miragon.blueprint.adapter.inbound.cibseven;

import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.process.CancelBikeOrderProcessApi.FlowNodes;
import io.miragon.blueprint.process.ServiceTasks;
import org.cibseven.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.cibseven.bpm.client.task.ExternalTask;
import org.cibseven.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ExternalTaskSubscription(topicName = ServiceTasks.BIKE_LEASING_REQUEST_CANCELLATION)
public class RequestCancellationWorker extends BaseExternalTaskWorker {

    private final RequestOrderCancellationUseCase useCase;

    public RequestCancellationWorker(RequestOrderCancellationUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void executeTask(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        OrderId orderId = new OrderId(externalTask.getVariable(FlowNodes.StartEventCancellationRequired.Variables.ORDER_ID.getValue()));
        boolean cancellationPossible = useCase.requestCancellation(orderId);
        externalTaskService.complete(
                externalTask,
                Map.of(FlowNodes.ServiceTaskRequestCancellation.Variables.CANCELLATION_POSSIBLE.getValue(), cancellationPossible)
        );
    }
}
