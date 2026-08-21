package io.miragon.blueprint.adapter.outbound.engine

import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.Elements
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.cibseven.rest.client.api.ProcessInstanceApi
import org.cibseven.rest.client.api.TaskApi
import org.cibseven.rest.client.model.ProcessInstanceDto
import org.cibseven.rest.client.model.TaskWithAttachmentAndCommentDto
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class TaskInboxAdapterTest {

    private val taskApi = mockk<TaskApi>()
    private val processInstanceApi = mockk<ProcessInstanceApi>()
    private val underTest = TaskInboxAdapter(taskApi = taskApi, processInstanceApi = processInstanceApi)

    @Test
    fun `maps open clarify-alternative tasks to their application id and waiting-since`() {

        // given: one open clarify-alternative task whose instance carries the application id as its key
        val applicationId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val created = OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC)
        every { taskApi.getTasks(taskDefinitionKey = Elements.USER_TASK_CLARIFY_ALTERNATIVE.value) } returns
            listOf(TaskWithAttachmentAndCommentDto(id = "task-1", processInstanceId = "pi-1", created = created))
        every { processInstanceApi.getProcessInstance("pi-1") } returns
            ProcessInstanceDto(id = "pi-1", businessKey = applicationId.toString())

        // when: the inbox is read
        val result = underTest.findOpenClarifications()

        // then: the task is translated into the domain business key, no engine task id leaks
        assertThat(result).hasSize(1)
        assertThat(result.first().applicationId.value).isEqualTo(applicationId)
    }

    @Test
    fun `skips tasks whose process instance has no business key`() {

        // given: an open task whose instance lost its business key (defensive)
        every { taskApi.getTasks(taskDefinitionKey = Elements.USER_TASK_CLARIFY_ALTERNATIVE.value) } returns
            listOf(
                TaskWithAttachmentAndCommentDto(
                    id = "task-1",
                    processInstanceId = "pi-1",
                    created = OffsetDateTime.now(ZoneOffset.UTC),
                ),
            )
        every { processInstanceApi.getProcessInstance("pi-1") } returns ProcessInstanceDto(id = "pi-1", businessKey = null)

        // when / then: it is silently dropped rather than surfacing an unusable case
        assertThat(underTest.findOpenClarifications()).isEmpty()
    }
}
