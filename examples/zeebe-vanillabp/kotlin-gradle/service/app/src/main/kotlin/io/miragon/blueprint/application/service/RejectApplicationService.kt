package io.miragon.blueprint.application.service

import io.miragon.blueprint.application.port.inbound.RejectApplicationUseCase
import io.miragon.blueprint.application.port.outbound.NotificationPort
import io.miragon.blueprint.domain.leasing.LeasingApplication
import org.springframework.stereotype.Service

@Service
class RejectApplicationService(
    private val notification: NotificationPort,
) : RejectApplicationUseCase {

    override fun reject(application: LeasingApplication): LeasingApplication {
        notification.send("Your bike-leasing application was rejected", application)
        return application.reject()
    }
}
