package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.inbound.SendSignatureReminderUseCase;
import io.miragon.blueprint.application.port.outbound.NotificationPort;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.springframework.stereotype.Service;

@Service
public class SendSignatureReminderService implements SendSignatureReminderUseCase {

    private final NotificationPort notification;

    public SendSignatureReminderService(NotificationPort notification) {
        this.notification = notification;
    }

    @Override
    public void sendSignatureReminder(LeasingApplication application) {
        notification.send("Reminder: your leasing contract is still awaiting signature", application);
    }
}
