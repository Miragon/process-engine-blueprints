package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.inbound.RejectApplicationUseCase;
import io.miragon.blueprint.application.port.outbound.NotificationPort;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.springframework.stereotype.Service;

@Service
public class RejectApplicationService implements RejectApplicationUseCase {

    private final NotificationPort notification;

    public RejectApplicationService(NotificationPort notification) {
        this.notification = notification;
    }

    @Override
    public LeasingApplication reject(LeasingApplication application) {
        notification.send("Your bike-leasing application was rejected", application);
        return application.reject();
    }
}
