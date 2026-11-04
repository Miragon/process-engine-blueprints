package io.miragon.blueprint.adapter.inbound.zeebe;

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengine.worker.Variable;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase;
import io.miragon.blueprint.domain.bike.OrderId;
import org.springframework.stereotype.Component;

@Component
public class BookCostsWorker {

    private final BookCancellationCostsUseCase useCase;

    public BookCostsWorker(BookCancellationCostsUseCase useCase) {
        this.useCase = useCase;
    }

    @ProcessEngineWorker(topic = ServiceTasks.MIRAVELO_BOOK_COSTS)
    public void handle(@Variable String orderId) {
        useCase.bookCosts(new OrderId(orderId));
    }
}
