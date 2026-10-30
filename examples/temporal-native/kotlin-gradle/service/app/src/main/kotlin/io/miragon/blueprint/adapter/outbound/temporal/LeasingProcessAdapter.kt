package io.miragon.blueprint.adapter.outbound.temporal

import io.miragon.blueprint.adapter.process.AlternativeDecision
import io.miragon.blueprint.adapter.process.BIKE_LEASING_TASK_QUEUE
import io.miragon.blueprint.adapter.process.BikeLeasingWorkflow
import io.miragon.blueprint.adapter.process.LeasingRequest
import io.miragon.blueprint.application.port.outbound.LeasingProcess
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.leasing.LeasingApplication
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowNotFoundException
import io.temporal.client.WorkflowOptions
import io.temporal.client.WorkflowUpdateException
import org.springframework.stereotype.Component

/**
 * Drives Temporal with the application id as workflow id. A signal for a closed workflow is dropped,
 * like a Zeebe message nobody waits for: the services update the status either way.
 */
@Component
class LeasingProcessAdapter(
    private val client: WorkflowClient,
) : LeasingProcess {

    override fun submitRequest(application: LeasingApplication) {
        val workflowId = application.id.value.toString()
        val options = WorkflowOptions.newBuilder()
            .setWorkflowId(workflowId)
            .setTaskQueue(BIKE_LEASING_TASK_QUEUE)
            .build()
        val workflow = client.newWorkflowStub(BikeLeasingWorkflow::class.java, options)
        val request = LeasingRequest(
            applicationId = workflowId,
            bikeId = application.bikeId.value,
            monthlyNetIncome = application.monthlyNetIncome,
            age = application.age,
        )
        WorkflowClient.start(workflow::run, request)
    }

    override fun correlateContractSigned(id: ApplicationId) = signal(id) { it.contractSigned() }

    override fun correlateHandoverReported(id: ApplicationId) = signal(id) { it.handoverReported() }

    override fun correlateApplicationWithdrawn(id: ApplicationId) = signal(id) { it.applicationWithdrawn() }

    override fun completeAlternativeClarification(id: ApplicationId, alternativeFound: Boolean, bikeId: BikeId?) {
        try {
            workflow(id).clarifyAlternative(AlternativeDecision(alternativeFound, bikeId?.value))
        } catch (e: WorkflowNotFoundException) {
            throw noOpenClarification(id, e)
        } catch (e: WorkflowUpdateException) {
            throw noOpenClarification(id, e)
        }
    }

    private fun signal(id: ApplicationId, send: (BikeLeasingWorkflow) -> Unit) {
        try {
            send(workflow(id))
        } catch (_: WorkflowNotFoundException) {
            return
        }
    }

    private fun workflow(id: ApplicationId) =
        client.newWorkflowStub(BikeLeasingWorkflow::class.java, id.value.toString())

    private fun noOpenClarification(id: ApplicationId, cause: Exception) =
        NoSuchElementException("No open clarification for application ${id.value}").apply { initCause(cause) }
}
