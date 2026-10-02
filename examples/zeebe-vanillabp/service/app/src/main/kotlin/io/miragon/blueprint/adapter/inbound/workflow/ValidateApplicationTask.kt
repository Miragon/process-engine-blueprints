package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Errors
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.ServiceTasks
import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.inbound.ValidateApplicationUseCase
import io.miragon.blueprint.domain.leasing.ApplicationInvalidException
import io.vanillabp.spi.service.TaskException
import io.vanillabp.spi.service.WorkflowTask
import org.springframework.stereotype.Component

@Component
class ValidateApplicationTask(
    private val useCase: ValidateApplicationUseCase,
) : BikeLeasingWorkflow() {

    @WorkflowTask(taskDefinition = ServiceTasks.MIRAVELO_VALIDATE_APPLICATION)
    fun validateApplication(aggregate: LeasingApplicationAggregate) {
        try {
            useCase.validate(aggregate.toDomain())
        } catch (invalid: ApplicationInvalidException) {
            throw TaskException(invalid.reason, Errors.APPLICATION_INVALID.code)
        }
    }
}
