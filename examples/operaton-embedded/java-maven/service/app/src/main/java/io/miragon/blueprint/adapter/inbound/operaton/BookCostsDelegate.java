package io.miragon.blueprint.adapter.inbound.operaton;

import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi.FlowNodes;
import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase;
import io.miragon.blueprint.domain.bike.OrderId;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

@Component
public class BookCostsDelegate extends BaseDelegate {

    private final BookCancellationCostsUseCase useCase;

    public BookCostsDelegate(BookCancellationCostsUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    protected void executeTask(DelegateExecution execution) {
        OrderId orderId = new OrderId(
                (String) execution.getVariable(FlowNodes.StartEventCancellationRequired.Variables.ORDER_ID.getValue()));
        useCase.bookCosts(orderId);
    }
}
