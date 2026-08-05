package io.miragon.blueprint.adapter.inbound.operaton

import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi.Variables
import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase
import io.miragon.blueprint.domain.bike.OrderId
import org.operaton.bpm.engine.delegate.DelegateExecution
import org.springframework.stereotype.Component

@Component
class BookCostsDelegate(
    private val useCase: BookCancellationCostsUseCase,
) : BaseDelegate() {

    override fun executeTask(execution: DelegateExecution) {
        val orderId = OrderId(execution.getVariable(Variables.StartEventCancellationRequired.ORDER_ID.value) as String)
        useCase.bookCosts(orderId)
    }
}
