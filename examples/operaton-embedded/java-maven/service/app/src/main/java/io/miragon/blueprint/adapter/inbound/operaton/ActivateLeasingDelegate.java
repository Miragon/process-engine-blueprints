package io.miragon.blueprint.adapter.inbound.operaton;

import io.miragon.blueprint.application.port.inbound.ActivateLeasingUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

@Component
public class ActivateLeasingDelegate extends BaseDelegate {

    private final ActivateLeasingUseCase useCase;

    public ActivateLeasingDelegate(ActivateLeasingUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    protected void executeTask(DelegateExecution execution) {
        useCase.activate(ApplicationId.of(execution.getProcessBusinessKey()));
    }
}
