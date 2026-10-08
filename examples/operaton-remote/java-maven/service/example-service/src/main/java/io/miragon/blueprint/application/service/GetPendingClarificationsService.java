package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.inbound.GetPendingClarificationsQuery;
import io.miragon.blueprint.application.port.outbound.BikePortfolioRepository;
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.TaskInboxPort;
import io.miragon.blueprint.domain.bike.Bike;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.PendingClarification;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetPendingClarificationsService implements GetPendingClarificationsQuery {

    private final TaskInboxPort taskInbox;
    private final LeasingApplicationRepository repository;
    private final BikePortfolioRepository bikePortfolio;

    public GetPendingClarificationsService(
            TaskInboxPort taskInbox,
            LeasingApplicationRepository repository,
            BikePortfolioRepository bikePortfolio) {
        this.taskInbox = taskInbox;
        this.repository = repository;
        this.bikePortfolio = bikePortfolio;
    }

    @Override
    public List<PendingClarification> pending() {
        List<TaskInboxPort.OpenClarification> openTasks = taskInbox.findOpenClarifications();
        // Pair each open task with its application; skip tasks whose application vanished (defensive).
        List<Map.Entry<TaskInboxPort.OpenClarification, LeasingApplication>> cases = openTasks.stream()
                .flatMap(task -> repository.findById(task.applicationId())
                        .map(application -> Map.entry(task, application))
                        .stream())
                .toList();
        Map<BikeId, String> models = bikePortfolio
                .findAllByIds(cases.stream().map(entry -> entry.getValue().bikeId()).toList())
                .stream()
                .collect(Collectors.toMap(Bike::bikeId, Bike::model, (existing, replacement) -> replacement));
        return cases.stream()
                .map(entry -> {
                    TaskInboxPort.OpenClarification task = entry.getKey();
                    LeasingApplication application = entry.getValue();
                    return new PendingClarification(
                            application.id(),
                            application.customerName(),
                            application.bikeId(),
                            models.get(application.bikeId()),
                            task.waitingSince());
                })
                .toList();
    }
}
