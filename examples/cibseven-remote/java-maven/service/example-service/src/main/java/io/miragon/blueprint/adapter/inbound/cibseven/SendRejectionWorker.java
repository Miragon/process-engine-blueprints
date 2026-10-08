package io.miragon.blueprint.adapter.inbound.cibseven;

import io.miragon.blueprint.application.port.inbound.RejectApplicationUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.process.ServiceTasks;
import org.cibseven.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.cibseven.bpm.client.task.ExternalTask;
import org.cibseven.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;

@Component
@ExternalTaskSubscription(topicName = ServiceTasks.BIKE_LEASING_SEND_REJECTION)
public class SendRejectionWorker extends BaseExternalTaskWorker {

    private final RejectApplicationUseCase useCase;

    public SendRejectionWorker(RejectApplicationUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void executeTask(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        useCase.reject(ApplicationId.of(externalTask.getBusinessKey()));
        externalTaskService.complete(externalTask);
    }
}
