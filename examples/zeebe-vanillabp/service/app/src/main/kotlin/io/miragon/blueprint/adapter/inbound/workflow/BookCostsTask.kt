package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.ServiceTasks
import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase
import io.vanillabp.spi.service.WorkflowTask
import org.springframework.stereotype.Component

@Component
class BookCostsTask(
    private val useCase: BookCancellationCostsUseCase,
) : BikeLeasingWorkflow() {

    @WorkflowTask(taskDefinition = ServiceTasks.MIRAVELO_BOOK_COSTS)
    fun bookCosts(aggregate: LeasingApplicationAggregate) {
        useCase.bookCosts(aggregate.placedOrderId())
    }
}
