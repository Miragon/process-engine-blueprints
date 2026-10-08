package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.inbound.RejectApplicationUseCase
import io.miragon.blueprint.domain.leasing.LeasingStatus
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SendRejectionTaskTest {

    private val useCase = mockk<RejectApplicationUseCase>()
    private val task = SendRejectionTask(useCase)

    @Test
    fun `adopts the rejected application on the workflow aggregate`() {
        // given
        val application = testLeasingApplication()
        val aggregate = LeasingApplicationAggregate.of(application)
        every { useCase.reject(application) } returns application.reject()

        // when
        task.sendRejection(aggregate)

        // then
        assertThat(aggregate.status).isEqualTo(LeasingStatus.REJECTED)
    }
}
