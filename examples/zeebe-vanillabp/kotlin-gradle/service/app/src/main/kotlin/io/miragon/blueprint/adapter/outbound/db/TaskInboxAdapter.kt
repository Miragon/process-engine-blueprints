package io.miragon.blueprint.adapter.outbound.db

import io.miragon.blueprint.application.port.outbound.TaskInboxPort
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.springframework.stereotype.Component

@Component
class TaskInboxAdapter(
    private val repository: LeasingApplicationJpaRepository,
) : TaskInboxPort {

    override fun findOpenClarifications(): List<TaskInboxPort.OpenClarification> =
        repository.findAllByClarificationRequestedAtIsNotNull().mapNotNull { aggregate ->
            aggregate.clarificationRequestedAt?.let { requestedAt ->
                TaskInboxPort.OpenClarification(
                    applicationId = ApplicationId.of(aggregate.applicationId),
                    waitingSince = requestedAt,
                )
            }
        }
}
