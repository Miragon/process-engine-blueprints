package io.miragon.blueprint.adapter.inbound.fluxnova

import io.miragon.blueprint.application.port.inbound.ValidateApplicationUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.leasing.ApplicationInvalidException
import org.finos.fluxnova.bpm.engine.delegate.BpmnError
import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution
import org.springframework.stereotype.Component

@Component
class ValidateApplicationDelegate(
    private val useCase: ValidateApplicationUseCase,
) : BaseDelegate() {

    override fun executeTask(execution: DelegateExecution) {
        try {
            useCase.validate(ApplicationId.of(execution.processBusinessKey))
        } catch (e: ApplicationInvalidException) {
            throw BpmnError("applicationInvalid", e.reason)
        }
    }
}
