package io.miragon.blueprint.adapter.inbound.operaton;

import io.miragon.blueprint.application.port.inbound.SendContractUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

@Component
public class SendContractDelegate extends BaseDelegate {

    private final SendContractUseCase useCase;

    public SendContractDelegate(SendContractUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    protected void executeTask(DelegateExecution execution) {
        useCase.sendContract(ApplicationId.of(execution.getProcessBusinessKey()));
    }
}
