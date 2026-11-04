package io.miragon.blueprint.adapter.outbound.zeebe

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.correlation.CorrelateMessageCmd
import dev.bpmcrafters.processengineapi.correlation.CorrelationApi
import dev.bpmcrafters.processengineapi.process.ProcessInformation
import dev.bpmcrafters.processengineapi.process.StartProcessApi
import dev.bpmcrafters.processengineapi.process.StartProcessByMessageCmd
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.support.UserTaskSupport
import io.miragon.blueprint.adapter.process.Messages
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.CompletableFuture

class LeasingProcessAdapterTest {

    private val startProcessApi = mockk<StartProcessApi>()
    private val correlationApi = mockk<CorrelationApi>()
    private val userTaskSupport = mockk<UserTaskSupport>()
    private val userTaskCompletionApi = mockk<UserTaskCompletionApi>()
    private val underTest = LeasingProcessAdapter(
        startProcessApi = startProcessApi,
        correlationApi = correlationApi,
        userTaskSupport = userTaskSupport,
        userTaskCompletionApi = userTaskCompletionApi,
    )

    private val id = ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))

    @Test
    fun `submitRequest starts the process by message with the application variables`() {
        // given: a leasing application and a captured start command
        val application = testLeasingApplication(id = id)
        val cmd = slot<StartProcessByMessageCmd>()
        every { startProcessApi.startProcess(capture(cmd)) } returns
            CompletableFuture.completedFuture(mockk<ProcessInformation>())

        // when: the request is submitted
        underTest.submitRequest(application)

        // then: the leasing-request message starts the process with the DMN inputs and the correlation variable
        assertThat(cmd.captured.messageName).isEqualTo(Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.value)
        assertThat(cmd.captured.get()).isEqualTo(
            mapOf(
                "applicationId" to id.value.toString(),
                "bikeId" to "BIKE-900",
                "monthlyNetIncome" to 3500.0,
                "age" to 35,
            ),
        )
    }

    @Test
    fun `completeAlternativeClarification resolves the task by application id and completes it`() {
        // given: an open task delivered to the user-task pool for this application
        every { userTaskSupport.getAllTasks() } returns listOf(task("task-1"))
        every { userTaskSupport.getPayload("task-1") } returns mapOf("applicationId" to id.value.toString())
        val cmd = slot<CompleteTaskCmd>()
        every { userTaskCompletionApi.completeTask(capture(cmd)) } returns CompletableFuture.completedFuture(Empty)

        // when: an alternative bike is selected from the outside
        underTest.completeAlternativeClarification(id, alternativeFound = true, bikeId = BikeId("BIKE-42"))

        // then: the resolved task is completed with the decision and the chosen bike
        assertThat(cmd.captured.taskId).isEqualTo("task-1")
        assertThat(cmd.captured.get()).isEqualTo(mapOf("alternativeFound" to true, "bikeId" to "BIKE-42"))
    }

    @Test
    fun `completeAlternativeClarification skips tasks of other applications and unreadable payloads`() {
        // given: a stale task whose payload lookup fails and a task of another application, before the matching one
        every { userTaskSupport.getAllTasks() } returns listOf(task("task-stale"), task("task-other"), task("task-1"))
        every { userTaskSupport.getPayload("task-stale") } throws IllegalArgumentException("unknown task")
        every { userTaskSupport.getPayload("task-other") } returns mapOf("applicationId" to UUID.randomUUID().toString())
        every { userTaskSupport.getPayload("task-1") } returns mapOf("applicationId" to id.value.toString())
        val cmd = slot<CompleteTaskCmd>()
        every { userTaskCompletionApi.completeTask(capture(cmd)) } returns CompletableFuture.completedFuture(Empty)

        // when: the clarification ends without an alternative
        underTest.completeAlternativeClarification(id, alternativeFound = false, bikeId = null)

        // then: only the matching task is completed, with the decision only
        verify(exactly = 1) { userTaskCompletionApi.completeTask(any()) }
        assertThat(cmd.captured.taskId).isEqualTo("task-1")
        assertThat(cmd.captured.get()).isEqualTo(mapOf("alternativeFound" to false))
    }

    @Test
    fun `completeAlternativeClarification waits for the task to be delivered`() {
        // given: the task reaches the pool on the second look
        every { userTaskSupport.getAllTasks() } returnsMany listOf(emptyList(), listOf(task("task-1")))
        every { userTaskSupport.getPayload("task-1") } returns mapOf("applicationId" to id.value.toString())
        val cmd = slot<CompleteTaskCmd>()
        every { userTaskCompletionApi.completeTask(capture(cmd)) } returns CompletableFuture.completedFuture(Empty)

        // when
        underTest.completeAlternativeClarification(id, alternativeFound = true, bikeId = null)

        // then
        assertThat(cmd.captured.taskId).isEqualTo("task-1")
        verify(exactly = 2) { userTaskSupport.getAllTasks() }
    }

    @Test
    fun `completeAlternativeClarification fails with NoSuchElementException when no task appears`() {
        // given: the token is not at the task, so the pool stays empty
        every { userTaskSupport.getAllTasks() } returns emptyList()

        // when / then: the REST advice maps this to a 409, and nothing is completed
        assertThatThrownBy { underTest.completeAlternativeClarification(id, alternativeFound = true, bikeId = null) }
            .isInstanceOf(NoSuchElementException::class.java)
        verify(exactly = 0) { userTaskCompletionApi.completeTask(any()) }
    }

    @Test
    fun `completeAlternativeClarification keeps the interrupt and fails unchecked when the wait is interrupted`() {
        // given: no task in the pool yet and an interrupted caller
        every { userTaskSupport.getAllTasks() } returns emptyList()
        Thread.currentThread().interrupt()
        try {
            // when / then: the wait aborts at once with an unchecked exception, which rolls back the use case
            assertThatThrownBy { underTest.completeAlternativeClarification(id, alternativeFound = true, bikeId = null) }
                .isExactlyInstanceOf(IllegalStateException::class.java)
                .hasCauseInstanceOf(InterruptedException::class.java)
            // and: the interrupt flag stays visible to the caller
            assertThat(Thread.currentThread().isInterrupted).isTrue()
        } finally {
            Thread.interrupted()
        }
        verify(exactly = 1) { userTaskSupport.getAllTasks() }
        verify(exactly = 0) { userTaskCompletionApi.completeTask(any()) }
    }

    @Test
    fun `correlateContractSigned publishes the message with the application id as correlation key`() {
        assertCorrelation(Messages.MIRAVELO_CONTRACT_SIGNED.value) { underTest.correlateContractSigned(id) }
    }

    @Test
    fun `correlateHandoverReported publishes the message with the application id as correlation key`() {
        assertCorrelation(Messages.MIRAVELO_HANDOVER_REPORTED.value) { underTest.correlateHandoverReported(id) }
    }

    @Test
    fun `correlateApplicationWithdrawn publishes the message with the application id as correlation key`() {
        assertCorrelation(Messages.MIRAVELO_APPLICATION_WITHDRAWN.value) { underTest.correlateApplicationWithdrawn(id) }
    }

    private fun assertCorrelation(messageName: String, correlate: () -> Unit) {
        val cmd = slot<CorrelateMessageCmd>()
        every { correlationApi.correlateMessage(capture(cmd)) } returns CompletableFuture.completedFuture(Empty)

        correlate()

        assertThat(cmd.captured.messageName).isEqualTo(messageName)
        assertThat(cmd.captured.correlation.get().correlationKey).isEqualTo(id.value.toString())
        // The adapter rejects every restriction except the tenant id.
        assertThat(cmd.captured.restrictions).isEmpty()
    }

    private fun task(taskId: String) = TaskInformation(taskId = taskId, meta = emptyMap())
}
