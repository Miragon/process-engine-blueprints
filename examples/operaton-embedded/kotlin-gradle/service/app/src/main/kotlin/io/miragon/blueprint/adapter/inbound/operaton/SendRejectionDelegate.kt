package io.miragon.blueprint.adapter.inbound.operaton

import io.miragon.blueprint.application.port.inbound.RejectApplicationUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.operaton.bpm.engine.delegate.DelegateExecution
import org.springframework.stereotype.Component

@Component
class SendRejectionDelegate(
    private val useCase: RejectApplicationUseCase,
) : BaseDelegate() {

    override fun executeTask(execution: DelegateExecution) {
        useCase.reject(ApplicationId.of(execution.processBusinessKey))
    }
}
