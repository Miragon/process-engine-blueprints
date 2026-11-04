package io.miragon.blueprint.adapter.inbound.zeebe;

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengine.worker.Variable;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.SendCancellationConfirmationUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import org.springframework.stereotype.Component;

@Component
public class SendCancellationConfirmationWorker {

    private final SendCancellationConfirmationUseCase useCase;

    public SendCancellationConfirmationWorker(SendCancellationConfirmationUseCase useCase) {
        this.useCase = useCase;
    }

    @ProcessEngineWorker(topic = ServiceTasks.MIRAVELO_SEND_CANCELLATION_CONFIRMATION)
    public void handle(@Variable String applicationId) {
        useCase.sendCancellationConfirmation(ApplicationId.of(applicationId));
    }
}
