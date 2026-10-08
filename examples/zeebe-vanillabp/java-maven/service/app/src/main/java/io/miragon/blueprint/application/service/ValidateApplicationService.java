package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.inbound.ValidateApplicationUseCase;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.springframework.stereotype.Service;

@Service
public class ValidateApplicationService implements ValidateApplicationUseCase {

    @Override
    public void validate(LeasingApplication application) {
        application.validate();
    }
}
