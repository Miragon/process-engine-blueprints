package io.miragon.blueprint.adapter.inbound.operaton;

import io.miragon.blueprint.application.port.inbound.ActivateLeasingUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.process.ServiceTasks;
import org.operaton.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.operaton.bpm.client.task.ExternalTask;
import org.operaton.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;

/**
 * Flips the read model to ACTIVE once the withdrawal period has elapsed (serviceTask_activateLeasing →
 * endEvent_leasingActive). The embedded blueprint runs this from a message-end-event delegate; the
 * remote engine cannot reach into the worker's database, so activation is its own external service
 * task handled here — the idiomatic remote counterpart.
 */
@Component
@ExternalTaskSubscription(topicName = ServiceTasks.BIKE_LEASING_ACTIVATE_LEASING)
public class ActivateLeasingWorker extends BaseExternalTaskWorker {

    private final ActivateLeasingUseCase useCase;

    public ActivateLeasingWorker(ActivateLeasingUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    protected void executeTask(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        useCase.activate(ApplicationId.of(externalTask.getBusinessKey()));
        externalTaskService.complete(externalTask);
    }
}
