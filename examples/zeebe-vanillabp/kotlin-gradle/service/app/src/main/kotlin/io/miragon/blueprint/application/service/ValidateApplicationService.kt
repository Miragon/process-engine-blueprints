package io.miragon.blueprint.application.service

import io.miragon.blueprint.application.port.inbound.ValidateApplicationUseCase
import io.miragon.blueprint.domain.leasing.LeasingApplication
import org.springframework.stereotype.Service

@Service
class ValidateApplicationService : ValidateApplicationUseCase {

    override fun validate(application: LeasingApplication) {
        application.validate()
    }
}
