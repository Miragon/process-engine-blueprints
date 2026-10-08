package io.miragon.blueprint.adapter.inbound.cibseven;

import io.miragon.blueprint.application.port.inbound.SendSignatureReminderUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.process.ServiceTasks;
import org.cibseven.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.cibseven.bpm.client.task.ExternalTask;
import org.cibseven.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;

@Component
@ExternalTaskSubscription(topicName = ServiceTasks.BIKE_LEASING_SEND_REMINDER_MAIL)
public class SendReminderMailWorker extends BaseExternalTaskWorker {

    private final SendSignatureReminderUseCase useCase;

    public SendReminderMailWorker(SendSignatureReminderUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void executeTask(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        useCase.sendSignatureReminder(ApplicationId.of(externalTask.getBusinessKey()));
        externalTaskService.complete(externalTask);
    }
}
