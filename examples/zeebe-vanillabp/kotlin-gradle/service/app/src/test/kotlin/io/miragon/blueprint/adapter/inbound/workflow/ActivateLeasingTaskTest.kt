package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.inbound.ActivateLeasingUseCase
import io.miragon.blueprint.domain.leasing.LeasingStatus
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ActivateLeasingTaskTest {

    private val useCase = mockk<ActivateLeasingUseCase>()
    private val task = ActivateLeasingTask(useCase)

    @Test
    fun `adopts the activated application on the workflow aggregate`() {
        // given
        val application = testLeasingApplication(status = LeasingStatus.HANDED_OVER)
        val aggregate = LeasingApplicationAggregate.of(application)
        every { useCase.activate(application) } returns application.activate()

        // when
        task.activateLeasing(aggregate)

        // then
        assertThat(aggregate.status).isEqualTo(LeasingStatus.ACTIVE)
    }
}
