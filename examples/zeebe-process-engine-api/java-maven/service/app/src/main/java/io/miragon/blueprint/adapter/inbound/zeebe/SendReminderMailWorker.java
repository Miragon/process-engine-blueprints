package io.miragon.blueprint.adapter.inbound.zeebe;

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengine.worker.Variable;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.SendSignatureReminderUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import org.springframework.stereotype.Component;

@Component
public class SendReminderMailWorker {

    private final SendSignatureReminderUseCase useCase;

    public SendReminderMailWorker(SendSignatureReminderUseCase useCase) {
        this.useCase = useCase;
    }

    @ProcessEngineWorker(topic = ServiceTasks.MIRAVELO_SEND_REMINDER_MAIL)
    public void handle(@Variable String applicationId) {
        useCase.sendSignatureReminder(ApplicationId.of(applicationId));
    }
}
