package io.miragon.blueprint.application.port.outbound

import io.miragon.blueprint.domain.leasing.ApplicationId
import java.time.LocalDateTime

/**
 * Reads the open clarification user tasks of the leasing workflow. Kept deliberately thin: it returns only
 * the business key (the application id) and when the task appeared, never an engine task id. The
 * back-office resolves a clarification through the domain (`POST .../clarify-alternative`), which
 * correlates by business key — so the UI never needs, and can never misuse, a raw task id.
 */
interface TaskInboxPort {
    fun findOpenClarifications(): List<OpenClarification>

    data class OpenClarification(
        val applicationId: ApplicationId,
        val waitingSince: LocalDateTime,
    )
}
