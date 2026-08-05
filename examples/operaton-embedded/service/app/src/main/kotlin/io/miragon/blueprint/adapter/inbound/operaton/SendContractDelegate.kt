package io.miragon.blueprint.adapter.inbound.operaton

import io.miragon.blueprint.application.port.inbound.SendContractUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.operaton.bpm.engine.delegate.DelegateExecution
import org.springframework.stereotype.Component

@Component
class SendContractDelegate(
    private val useCase: SendContractUseCase,
) : BaseDelegate() {

    override fun executeTask(execution: DelegateExecution) {
        useCase.sendContract(ApplicationId.of(execution.processBusinessKey))
    }
}
