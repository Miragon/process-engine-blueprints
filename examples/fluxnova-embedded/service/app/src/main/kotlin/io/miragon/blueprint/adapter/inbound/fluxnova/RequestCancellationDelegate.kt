package io.miragon.blueprint.adapter.inbound.fluxnova

import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi.Variables
import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase
import io.miragon.blueprint.domain.bike.OrderId
import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution
import org.springframework.stereotype.Component

@Component
class RequestCancellationDelegate(
    private val useCase: RequestOrderCancellationUseCase,
) : BaseDelegate() {

    override fun executeTask(execution: DelegateExecution) {
        // `orderId` is handed to the cancelBikeOrder sub-process by the calling activity.
        val orderId = OrderId(execution.getVariable(Variables.StartEventCancellationRequired.ORDER_ID.value) as String)
        val cancellationPossible = useCase.requestCancellation(orderId)
        execution.setVariable(Variables.ServiceTaskRequestCancellation.CANCELLATION_POSSIBLE.value, cancellationPossible)
    }
}
