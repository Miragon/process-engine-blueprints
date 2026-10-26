package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.inbound.ActivateLeasingUseCase;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.springframework.stereotype.Service;

@Service
public class ActivateLeasingService implements ActivateLeasingUseCase {

    @Override
    public LeasingApplication activate(LeasingApplication application) {
        return application.activate();
    }
}
