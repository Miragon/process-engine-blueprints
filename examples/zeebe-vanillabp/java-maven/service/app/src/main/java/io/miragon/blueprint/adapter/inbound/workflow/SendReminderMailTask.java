package io.miragon.blueprint.adapter.inbound.workflow;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.SendSignatureReminderUseCase;
import io.vanillabp.spi.service.WorkflowTask;
import org.springframework.stereotype.Component;

@Component
public class SendReminderMailTask extends BikeLeasingWorkflow {

    private final SendSignatureReminderUseCase useCase;

    public SendReminderMailTask(SendSignatureReminderUseCase useCase) {
        this.useCase = useCase;
    }

    @WorkflowTask(taskDefinition = ServiceTasks.MIRAVELO_SEND_REMINDER_MAIL)
    public void sendReminderMail(LeasingApplicationAggregate aggregate) {
        useCase.sendSignatureReminder(aggregate.toDomain());
    }
}
