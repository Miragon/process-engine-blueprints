package io.miragon.blueprint.adapter.inbound.fluxnova;

import io.miragon.blueprint.application.port.inbound.CancelContractUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

@Component
public class CancelContractDelegate extends BaseDelegate {

    private final CancelContractUseCase useCase;

    public CancelContractDelegate(CancelContractUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    protected void executeTask(DelegateExecution execution) {
        useCase.cancelContract(ApplicationId.of(execution.getProcessBusinessKey()));
    }
}
