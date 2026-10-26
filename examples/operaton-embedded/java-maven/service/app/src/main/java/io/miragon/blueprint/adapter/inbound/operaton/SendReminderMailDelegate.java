package io.miragon.blueprint.adapter.inbound.operaton;

import io.miragon.blueprint.application.port.inbound.SendSignatureReminderUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

@Component
public class SendReminderMailDelegate extends BaseDelegate {

    private final SendSignatureReminderUseCase useCase;

    public SendReminderMailDelegate(SendSignatureReminderUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    protected void executeTask(DelegateExecution execution) {
        useCase.sendSignatureReminder(ApplicationId.of(execution.getProcessBusinessKey()));
    }
}
