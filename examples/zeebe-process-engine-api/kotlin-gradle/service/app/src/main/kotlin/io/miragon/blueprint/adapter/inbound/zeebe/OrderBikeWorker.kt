package io.miragon.blueprint.adapter.inbound.zeebe

import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes
import io.miragon.blueprint.adapter.process.Errors
import io.miragon.blueprint.adapter.process.ServiceTasks
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.bike.BikeUnavailableException
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.springframework.stereotype.Component

/**
 * Orders the bike from the dealer and returns the placed order as the process variable `orderId`. An
 * out-of-stock bike is no failure: it is reported as the BPMN error `bikeUnavailable`, which the error
 * boundary event on the service task catches, and leaving the task this way registers no order
 * compensation. Any other exception reaches the worker library, which fails the job through the adapter:
 * `service-tasks.retries` and `retry-timeout-in-seconds` in `application.yaml` give the retry countdown
 * with a 10s backoff, and at 0 retries Zeebe raises an incident on `serviceTask_orderBike`. It powers the
 * reproducible incident demo (bike id `BIKE-FAIL`, see the `06-incident-demo` Bruno collection); the
 * poison-id logic itself lives in the simulated dealer, which throws.
 */
@Component
class OrderBikeWorker(
    private val useCase: OrderBikeUseCase,
) {

    @ProcessEngineWorker(topic = ServiceTasks.MIRAVELO_ORDER_BIKE)
    fun handle(@Variable applicationId: String, @Variable bikeId: String): Map<String, Any> {
        val orderId = try {
            useCase.orderBike(ApplicationId.of(applicationId), BikeId(bikeId))
        } catch (e: BikeUnavailableException) {
            throw BpmnErrorOccurred(e.message.orEmpty(), Errors.BIKE_UNAVAILABLE.code, emptyMap())
        }
        return mapOf(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.value to orderId.value)
    }
}
