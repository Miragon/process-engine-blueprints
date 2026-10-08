package io.miragon.blueprint.adapter.inbound.operaton;

import io.miragon.blueprint.application.port.inbound.SendCancellationConfirmationUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

@Component
public class SendCancellationConfirmationDelegate extends BaseDelegate {

    private final SendCancellationConfirmationUseCase useCase;

    public SendCancellationConfirmationDelegate(SendCancellationConfirmationUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    protected void executeTask(DelegateExecution execution) {
        useCase.sendCancellationConfirmation(ApplicationId.of(execution.getProcessBusinessKey()));
    }
}
