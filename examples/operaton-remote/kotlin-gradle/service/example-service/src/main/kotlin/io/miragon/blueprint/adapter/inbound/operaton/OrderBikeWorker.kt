package io.miragon.blueprint.adapter.inbound.operaton

import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.miragon.blueprint.domain.bike.BikeUnavailableException
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.process.Errors
import io.miragon.blueprint.process.ServiceTasks
import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.FlowNodes
import org.operaton.bpm.client.spring.annotation.ExternalTaskSubscription
import org.operaton.bpm.client.task.ExternalTask
import org.operaton.bpm.client.task.ExternalTaskService
import org.springframework.stereotype.Component

@Component
@ExternalTaskSubscription(topicName = ServiceTasks.BIKE_LEASING_ORDER_BIKE)
class OrderBikeWorker(
    private val useCase: OrderBikeUseCase,
) : BaseExternalTaskWorker() {

    // Incident demo: the order task is its own external task (its own unit of work), so a dealer
    // "outage" for BIKE-FAIL fails this task specifically. Retry it 3 times, 10s apart, so the
    // countdown is visible in the Operaton Cockpit before an incident is raised on serviceTask_orderBike
    // (~30s) — the remote counterpart to a delegate's `failedJobRetryTimeCycle` R3/PT10S. This is the
    // only worker that retries; every other task keeps the base "fail fast, raise the incident" default.
    // Reproduce it with the 06-incident-demo Bruno collection.
    override val failureRetries: Int = 3
    override val failureRetryTimeoutMs: Long = 10_000L

    override fun executeTask(externalTask: ExternalTask, externalTaskService: ExternalTaskService) {
        val orderId = try {
            useCase.orderBike(ApplicationId.of(externalTask.businessKey))
        } catch (e: BikeUnavailableException) {
            // Raise the `bikeUnavailable` BPMN error so the error boundary event diverts to the
            // alternative clarification. Leaving the task this way registers no order compensation.
            externalTaskService.handleBpmnError(externalTask, Errors.BIKE_UNAVAILABLE.code, e.message)
            return
        }
        // Output variable the order compensation later reuses.
        externalTaskService.complete(
            externalTask,
            mapOf(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.value to orderId.value),
        )
    }
}
