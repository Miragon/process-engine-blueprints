package io.miragon.blueprint.adapter.inbound.workflow;

import io.miragon.blueprint.adapter.process.Errors;
import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.ValidateApplicationUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationInvalidException;
import io.vanillabp.spi.service.TaskException;
import io.vanillabp.spi.service.WorkflowTask;
import org.springframework.stereotype.Component;

@Component
public class ValidateApplicationTask extends BikeLeasingWorkflow {

    private final ValidateApplicationUseCase useCase;

    public ValidateApplicationTask(ValidateApplicationUseCase useCase) {
        this.useCase = useCase;
    }

    @WorkflowTask(taskDefinition = ServiceTasks.MIRAVELO_VALIDATE_APPLICATION)
    public void validateApplication(LeasingApplicationAggregate aggregate) {
        try {
            useCase.validate(aggregate.toDomain());
        } catch (ApplicationInvalidException invalid) {
            throw new TaskException(invalid.getReason(), Errors.APPLICATION_INVALID.getCode());
        }
    }
}
