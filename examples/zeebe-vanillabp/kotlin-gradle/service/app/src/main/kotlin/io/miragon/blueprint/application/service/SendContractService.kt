package io.miragon.blueprint.application.service

import io.miragon.blueprint.application.port.inbound.SendContractUseCase
import io.miragon.blueprint.application.port.outbound.ContractPort
import io.miragon.blueprint.application.port.outbound.NotificationPort
import io.miragon.blueprint.domain.leasing.LeasingApplication
import org.springframework.stereotype.Service

@Service
class SendContractService(
    private val contract: ContractPort,
    private val notification: NotificationPort,
) : SendContractUseCase {

    override fun sendContract(application: LeasingApplication): LeasingApplication {
        if (application.contractId != null) return application
        val contractId = contract.issueContract(application.id)
        notification.send("Please review and sign your leasing contract", application)
        return application.withContract(contractId)
    }
}
