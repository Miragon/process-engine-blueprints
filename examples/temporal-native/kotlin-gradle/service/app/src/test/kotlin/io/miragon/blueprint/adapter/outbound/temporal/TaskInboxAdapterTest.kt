package io.miragon.blueprint.adapter.outbound.temporal

import io.miragon.blueprint.adapter.process.CLARIFICATION_WAITING_SINCE
import io.miragon.blueprint.application.port.outbound.TaskInboxPort
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.mockk.every
import io.mockk.mockk
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowExecutionMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.stream.Stream

class TaskInboxAdapterTest {

    private val client = mockk<WorkflowClient>()
    private val adapter = TaskInboxAdapter(client)

    @Test
    fun `lists the running workflows that wait for a clarification`() {
        // given
        val applicationId = UUID.randomUUID().toString()
        val waitingSince = OffsetDateTime.of(2026, 10, 30, 12, 0, 0, 0, ZoneOffset.UTC)
        val execution = mockk<WorkflowExecutionMetadata>(relaxed = true)
        every { execution.execution.workflowId } returns applicationId
        every { execution.typedSearchAttributes.get(CLARIFICATION_WAITING_SINCE) } returns waitingSince
        every {
            client.listExecutions("ClarificationWaitingSince IS NOT NULL AND ExecutionStatus = 'Running'")
        } returns Stream.of(execution)

        // when
        val clarifications = adapter.findOpenClarifications()

        // then
        assertThat(clarifications).containsExactly(
            TaskInboxPort.OpenClarification(ApplicationId.of(applicationId), waitingSince.toLocalDateTime()),
        )
    }
}
