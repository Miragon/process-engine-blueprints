package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.inbound.SendCancellationConfirmationUseCase
import io.miragon.blueprint.domain.leasing.LeasingStatus
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SendCancellationConfirmationTaskTest {

    private val useCase = mockk<SendCancellationConfirmationUseCase>()
    private val task = SendCancellationConfirmationTask(useCase)

    @Test
    fun `adopts the cancelled application on the workflow aggregate`() {
        // given
        val application = testLeasingApplication(status = LeasingStatus.WITHDRAWN)
        val aggregate = LeasingApplicationAggregate.of(application)
        every { useCase.sendCancellationConfirmation(application) } returns application.cancel()

        // when
        task.sendCancellationConfirmation(aggregate)

        // then
        assertThat(aggregate.status).isEqualTo(LeasingStatus.CANCELLED)
    }
}
