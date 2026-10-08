package io.miragon.blueprint.adapter.inbound.cibseven

import io.miragon.blueprint.application.port.inbound.ValidateApplicationUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.leasing.ApplicationInvalidException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.cibseven.bpm.client.task.ExternalTask
import org.cibseven.bpm.client.task.ExternalTaskService
import org.junit.jupiter.api.Test
import java.util.UUID

class ValidateApplicationWorkerTest {

    private val useCase = mockk<ValidateApplicationUseCase>(relaxed = true)
    private val underTest = ValidateApplicationWorker(useCase)

    private val applicationId = ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
    private val task = mockk<ExternalTask>(relaxed = true) {
        every { businessKey } returns applicationId.value.toString()
    }
    private val service = mockk<ExternalTaskService>(relaxed = true)

    @Test
    fun `completes the task for a valid application`() {

        // given: validation succeeds
        every { useCase.validate(applicationId) } returns Unit

        // when: the worker runs
        underTest.execute(task, service)

        // then: the external task is completed and no BPMN error is raised
        verify { useCase.validate(applicationId) }
        verify { service.complete(task) }
        verify(exactly = 0) { service.handleBpmnError(any(), any(), any()) }
    }

    @Test
    fun `raises the applicationInvalid BPMN error when validation fails`() {

        // given: validation surfaces the application as invalid
        every { useCase.validate(applicationId) } throws
            ApplicationInvalidException(applicationId, "no income")

        // when: the worker runs
        underTest.execute(task, service)

        // then: the BPMN error is raised and the task is not completed
        verify { service.handleBpmnError(task, "applicationInvalid", "no income") }
        verify(exactly = 0) { service.complete(any()) }
    }
}
