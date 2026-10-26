package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.inbound.SendCancellationConfirmationUseCase;
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.NotificationPort;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SendCancellationConfirmationService implements SendCancellationConfirmationUseCase {

    private final LeasingApplicationRepository repository;
    private final NotificationPort notification;

    public SendCancellationConfirmationService(
            LeasingApplicationRepository repository,
            NotificationPort notification) {
        this.repository = repository;
        this.notification = notification;
    }

    @Override
    public void sendCancellationConfirmation(ApplicationId id) {
        LeasingApplication application = repository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Unknown application " + id.value()));
        notification.send("Your bike-leasing application has been cancelled", application);
        repository.save(application.cancel());
    }
}
