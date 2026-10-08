package io.miragon.blueprint.adapter.inbound.operaton

import io.miragon.blueprint.application.port.inbound.IssueInsurancePolicyUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.operaton.bpm.engine.delegate.DelegateExecution
import org.springframework.stereotype.Component

@Component
class IssueInsurancePolicyDelegate(
    private val useCase: IssueInsurancePolicyUseCase,
) : BaseDelegate() {

    override fun executeTask(execution: DelegateExecution) {
        useCase.issuePolicy(ApplicationId.of(execution.processBusinessKey))
    }
}
