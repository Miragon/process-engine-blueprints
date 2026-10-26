package io.miragon.blueprint.adapter.inbound.operaton

import io.miragon.blueprint.application.port.inbound.CancelInsurancePolicyUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.operaton.bpm.engine.delegate.DelegateExecution
import org.springframework.stereotype.Component

@Component
class CancelPolicyDelegate(
    private val useCase: CancelInsurancePolicyUseCase,
) : BaseDelegate() {

    override fun executeTask(execution: DelegateExecution) {
        useCase.cancelPolicy(ApplicationId.of(execution.processBusinessKey))
    }
}
