package io.miragon.blueprint.adapter.inbound.temporal

import io.miragon.blueprint.adapter.process.BIKE_LEASING_TASK_QUEUE
import io.miragon.blueprint.adapter.process.BikeLeasingActivities
import io.miragon.blueprint.application.port.inbound.IssueInsurancePolicyUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.temporal.spring.boot.ActivityImpl
import org.springframework.stereotype.Component

@Component
@ActivityImpl(taskQueues = [BIKE_LEASING_TASK_QUEUE])
class IssueInsurancePolicyActivity(
    private val useCase: IssueInsurancePolicyUseCase,
) : BikeLeasingActivities.IssueInsurancePolicy {

    override fun issueInsurancePolicy(applicationId: String) = useCase.issuePolicy(ApplicationId.of(applicationId))
}
