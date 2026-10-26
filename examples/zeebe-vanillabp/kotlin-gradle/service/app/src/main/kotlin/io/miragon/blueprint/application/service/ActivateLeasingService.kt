package io.miragon.blueprint.application.service

import io.miragon.blueprint.application.port.inbound.ActivateLeasingUseCase
import io.miragon.blueprint.domain.leasing.LeasingApplication
import org.springframework.stereotype.Service

@Service
class ActivateLeasingService : ActivateLeasingUseCase {

    override fun activate(application: LeasingApplication): LeasingApplication = application.activate()
}
