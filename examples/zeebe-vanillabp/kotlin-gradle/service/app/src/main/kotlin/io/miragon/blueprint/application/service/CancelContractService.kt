package io.miragon.blueprint.application.service

import io.miragon.blueprint.application.port.inbound.CancelContractUseCase
import io.miragon.blueprint.application.port.outbound.ContractPort
import io.miragon.blueprint.domain.leasing.LeasingApplication
import org.springframework.stereotype.Service

@Service
class CancelContractService(
    private val contract: ContractPort,
) : CancelContractUseCase {

    override fun cancelContract(application: LeasingApplication) {
        val contractId = application.contractId ?: error("No contract issued for application ${application.id}")
        contract.revokeContract(contractId)
    }
}
