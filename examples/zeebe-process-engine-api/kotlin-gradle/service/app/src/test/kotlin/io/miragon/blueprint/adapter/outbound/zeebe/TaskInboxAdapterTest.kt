package io.miragon.blueprint.adapter.outbound.zeebe

import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.support.UserTaskSupport
import io.miragon.blueprint.application.port.outbound.TaskInboxPort
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class TaskInboxAdapterTest {

    private val userTaskSupport = mockk<UserTaskSupport>()
    private val underTest = TaskInboxAdapter(userTaskSupport)

    private val applicationId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000").toString()

    @Test
    fun `translates pooled tasks into their application id and creation date`() {
        // given: one delivered task carrying the application id
        every { userTaskSupport.getAllTasks() } returns listOf(task("task-1", "2026-06-23T09:00:00Z"))
        every { userTaskSupport.getPayload("task-1") } returns mapOf("applicationId" to applicationId)

        // when: the inbox is read
        val result = underTest.findOpenClarifications()

        // then: the task surfaces as its case, never as an engine task id
        assertThat(result).containsExactly(
            TaskInboxPort.OpenClarification(
                applicationId = ApplicationId.of(applicationId),
                waitingSince = LocalDateTime.of(2026, 6, 23, 9, 0),
            ),
        )
    }

    @Test
    fun `skips a task that left the pool while the inbox is read`() {
        // given: the payload of a listed task is already gone
        every { userTaskSupport.getAllTasks() } returns listOf(task("task-gone", "2026-06-23T09:00:00Z"))
        every { userTaskSupport.getPayload("task-gone") } throws IllegalArgumentException("unknown task")

        // when / then
        assertThat(underTest.findOpenClarifications()).isEmpty()
    }

    @Test
    fun `skips a task without an application id`() {
        // given
        every { userTaskSupport.getAllTasks() } returns listOf(task("task-1", "2026-06-23T09:00:00Z"))
        every { userTaskSupport.getPayload("task-1") } returns emptyMap()

        // when / then
        assertThat(underTest.findOpenClarifications()).isEmpty()
    }

    @Test
    fun `skips a task without a creation date`() {
        // given
        every { userTaskSupport.getAllTasks() } returns
            listOf(TaskInformation("task-1", mapOf("activityId" to "userTask_clarifyAlternative")))
        every { userTaskSupport.getPayload("task-1") } returns mapOf("applicationId" to applicationId)

        // when / then
        assertThat(underTest.findOpenClarifications()).isEmpty()
    }

    @Test
    fun `skips a task of another user task`() {
        // given: an open clarify-return task of the same application in the pool
        every { userTaskSupport.getAllTasks() } returns
            listOf(task("task-1", "2026-06-23T09:00:00Z", activityId = "userTask_clarifyReturn"))
        every { userTaskSupport.getPayload("task-1") } returns mapOf("applicationId" to applicationId)

        // when / then: the inbox lists clarify-alternative tasks only
        assertThat(underTest.findOpenClarifications()).isEmpty()
    }

    private fun task(taskId: String, creationDate: String, activityId: String = "userTask_clarifyAlternative") =
        TaskInformation(taskId = taskId, meta = mapOf("activityId" to activityId, "creationDate" to creationDate))
}
