package io.miragon.blueprint.adapter.inbound.cibseven

import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.ServiceTasks
import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.Variables
import org.cibseven.bpm.client.spring.annotation.ExternalTaskSubscription
import org.cibseven.bpm.client.task.ExternalTask
import org.cibseven.bpm.client.task.ExternalTaskService
import org.springframework.stereotype.Component

@Component
@ExternalTaskSubscription(topicName = ServiceTasks.BIKE_LEASING_ORDER_BIKE)
class OrderBikeWorker(
    private val useCase: OrderBikeUseCase,
) : BaseExternalTaskWorker() {

    // Incident demo: the order task is its own external task (its own unit of work), so a dealer
    // "outage" for BIKE-FAIL fails this task specifically. Retry it 3 times, 10s apart, so the
    // countdown is visible in the CIB seven Cockpit before an incident is raised on serviceTask_orderBike
    // (~30s) — the remote counterpart to a delegate's `failedJobRetryTimeCycle` R3/PT10S. This is the
    // only worker that retries; every other task keeps the base "fail fast, raise the incident" default.
    // Reproduce it with the 06-incident-demo Bruno collection.
    override val failureRetries: Int = 3
    override val failureRetryTimeoutMs: Long = 10_000L

    override fun executeTask(externalTask: ExternalTask, externalTaskService: ExternalTaskService) {
        val result = useCase.orderBike(ApplicationId.of(externalTask.businessKey))
        // Output variables the process routes on (`bikeAvailable`) and later reuses (`orderId`).
        externalTaskService.complete(
            externalTask,
            mapOf(
                Variables.ServiceTaskOrderBike.ORDER_ID.value to result.orderId?.value,
                Variables.ServiceTaskOrderBike.BIKE_AVAILABLE.value to result.bikeAvailable,
            ),
        )
    }
}
