package io.miragon.blueprint.adapter.inbound.operaton

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes
import io.miragon.blueprint.adapter.process.Errors
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.miragon.blueprint.domain.bike.BikeUnavailableException
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.operaton.bpm.engine.delegate.BpmnError
import org.operaton.bpm.engine.delegate.DelegateExecution
import org.springframework.stereotype.Component

@Component
class OrderBikeDelegate(
    private val useCase: OrderBikeUseCase,
) : BaseDelegate() {

    override fun executeTask(execution: DelegateExecution) {
        val orderId = try {
            useCase.orderBike(ApplicationId.of(execution.processBusinessKey))
        } catch (e: BikeUnavailableException) {
            throw BpmnError(Errors.BIKE_UNAVAILABLE.code, e.message)
        }
        execution.setVariable(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.value, orderId.value)
    }
}
