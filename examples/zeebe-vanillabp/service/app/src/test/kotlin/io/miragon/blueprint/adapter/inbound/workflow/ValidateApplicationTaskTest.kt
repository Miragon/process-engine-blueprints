package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.inbound.ValidateApplicationUseCase
import io.miragon.blueprint.domain.leasing.ApplicationInvalidException
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.vanillabp.spi.service.TaskException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.junit.jupiter.api.Test

class ValidateApplicationTaskTest {

    private val useCase = mockk<ValidateApplicationUseCase>()
    private val task = ValidateApplicationTask(useCase)

    @Test
    fun `completes for a valid application`() {
        // given
        val application = testLeasingApplication()
        every { useCase.validate(application) } just Runs

        // when
        task.validateApplication(LeasingApplicationAggregate.of(application))

        // then
        verify { useCase.validate(application) }
    }

    @Test
    fun `raises the applicationInvalid BPMN error for an invalid application`() {
        // given
        val application = testLeasingApplication()
        every { useCase.validate(application) } throws ApplicationInvalidException(application.id, "too young")

        // when
        val error = catchThrowableOfType(TaskException::class.java) {
            task.validateApplication(LeasingApplicationAggregate.of(application))
        }

        // then
        assertThat(error.errorCode).isEqualTo("applicationInvalid")
        assertThat(error.errorName).isEqualTo("too young")
    }
}
