package io.miragon.blueprint.adapter.outbound.db;

import io.miragon.blueprint.application.port.outbound.TaskInboxPort;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TaskInboxAdapter implements TaskInboxPort {

    private final LeasingApplicationJpaRepository repository;

    public TaskInboxAdapter(LeasingApplicationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<TaskInboxPort.OpenClarification> findOpenClarifications() {
        return repository.findAllByClarificationRequestedAtIsNotNull().stream()
                .map(aggregate -> new TaskInboxPort.OpenClarification(
                        ApplicationId.of(aggregate.getApplicationId()),
                        aggregate.getClarificationRequestedAt()))
                .toList();
    }
}
