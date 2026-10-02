package io.miragon.blueprint.adapter.outbound.workflow

import io.miragon.blueprint.adapter.outbound.db.LeasingApplicationJpaRepository
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Messages
import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.outbound.LeasingProcess
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.leasing.LeasingApplication
import io.miragon.bpmn.runtime.MessageName
import io.vanillabp.spi.process.ProcessService
import org.springframework.stereotype.Component

@Component
class LeasingProcessAdapter(
    private val processService: ProcessService<LeasingApplicationAggregate>,
    private val aggregates: LeasingApplicationJpaRepository,
) : LeasingProcess {

    override fun submitRequest(application: LeasingApplication) {
        processService.startWorkflowByMessage(
            LeasingApplicationAggregate.of(application),
            Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.value,
        )
    }

    override fun correlateContractSigned(id: ApplicationId) =
        correlate(id, Messages.MIRAVELO_CONTRACT_SIGNED)

    override fun correlateHandoverReported(id: ApplicationId) =
        correlate(id, Messages.MIRAVELO_HANDOVER_REPORTED)

    override fun correlateApplicationWithdrawn(id: ApplicationId) =
        correlate(id, Messages.MIRAVELO_APPLICATION_WITHDRAWN)

    override fun completeAlternativeClarification(
        id: ApplicationId,
        alternativeFound: Boolean,
    ) {
        val aggregate = aggregateOf(id)
        val taskId = aggregate.resolveClarification(alternativeFound)
        processService.completeUserTask(aggregate, taskId)
    }

    private fun correlate(id: ApplicationId, message: MessageName) {
        processService.correlateMessage(aggregateOf(id), message.value)
    }

    private fun aggregateOf(id: ApplicationId): LeasingApplicationAggregate =
        aggregates.findById(id.value.toString())
            .orElseThrow { IllegalStateException("Unknown application ${id.value}") }
}
