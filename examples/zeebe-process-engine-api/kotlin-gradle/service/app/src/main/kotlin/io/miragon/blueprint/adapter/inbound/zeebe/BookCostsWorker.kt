package io.miragon.blueprint.adapter.inbound.zeebe

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import io.miragon.blueprint.adapter.process.ServiceTasks
import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase
import io.miragon.blueprint.domain.bike.OrderId
import org.springframework.stereotype.Component

@Component
class BookCostsWorker(
    private val useCase: BookCancellationCostsUseCase,
) {

    @ProcessEngineWorker(topic = ServiceTasks.MIRAVELO_BOOK_COSTS)
    fun handle(@Variable orderId: String) {
        useCase.bookCosts(OrderId(orderId))
    }
}
