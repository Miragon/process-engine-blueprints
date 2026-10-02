package io.miragon.blueprint.adapter.outbound.workflow

import io.miragon.blueprint.adapter.outbound.db.LeasingApplicationJpaRepository
import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.vanillabp.spi.process.ProcessService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Optional

class LeasingProcessAdapterTest {

    private val processService = mockk<ProcessService<LeasingApplicationAggregate>>()
    private val aggregates = mockk<LeasingApplicationJpaRepository>()
    private val underTest = LeasingProcessAdapter(processService = processService, aggregates = aggregates)

    private val application = testLeasingApplication()
    private val aggregate = LeasingApplicationAggregate.of(application)

    @Test
    fun `submitRequest starts the workflow by the leasing-request message with the application as its aggregate`() {

        // given: a process service that accepts the start
        val started = slot<LeasingApplicationAggregate>()
        every { processService.startWorkflowByMessage(capture(started), any<String>()) } answers { firstArg() }

        // when: the request is submitted
        underTest.submitRequest(application)

        // then: the workflow is started for an aggregate of that application
        verify { processService.startWorkflowByMessage(any(), "miravelo.leasingRequestReceived") }
        assertThat(started.captured.toDomain()).isEqualTo(application)
        confirmVerified(processService, aggregates)
    }

    @Test
    fun `correlateContractSigned correlates the contract-signed message to the workflow of the application`() {

        // given: the stored aggregate of the application
        knownAggregate()
        every { processService.correlateMessage(any(), any<String>()) } answers { firstArg() }

        // when: the signature is correlated
        underTest.correlateContractSigned(application.id)

        // then: the message is correlated for exactly that aggregate
        verify { processService.correlateMessage(aggregate, "miravelo.contractSigned") }
    }

    @Test
    fun `correlateHandoverReported correlates the handover-reported message to the workflow of the application`() {

        // given: the stored aggregate of the application
        knownAggregate()
        every { processService.correlateMessage(any(), any<String>()) } answers { firstArg() }

        // when: the handover is correlated
        underTest.correlateHandoverReported(application.id)

        // then: the message is correlated for exactly that aggregate
        verify { processService.correlateMessage(aggregate, "miravelo.handoverReported") }
    }

    @Test
    fun `correlateApplicationWithdrawn correlates the application-withdrawn message to the workflow of the application`() {

        // given: the stored aggregate of the application
        knownAggregate()
        every { processService.correlateMessage(any(), any<String>()) } answers { firstArg() }

        // when: the withdrawal is correlated
        underTest.correlateApplicationWithdrawn(application.id)

        // then: the message is correlated for exactly that aggregate
        verify { processService.correlateMessage(aggregate, "miravelo.applicationWithdrawn") }
    }

    @Test
    fun `correlating for an unknown application fails without touching the engine`() {

        // given: no aggregate for the application
        every { aggregates.findById(application.id.value.toString()) } returns Optional.empty()

        // when / then: the correlation is refused
        assertThatThrownBy { underTest.correlateContractSigned(application.id) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining(application.id.value.toString())
        confirmVerified(processService)
    }

    @Test
    fun `completeAlternativeClarification records the decision and completes the remembered user task`() {

        // given: an aggregate parked on the clarification user task
        aggregate.clarificationRequested("4711", LocalDateTime.of(2026, 3, 1, 8, 0))
        knownAggregate()
        every { processService.completeUserTask(any(), any()) } answers { firstArg() }

        // when: the clarification is completed with an alternative
        underTest.completeAlternativeClarification(application.id, alternativeFound = true)

        // then: the decision is on the aggregate and the remembered task is completed
        assertThat(aggregate.alternativeFound).isTrue()
        assertThat(aggregate.clarifyAlternativeTaskId).isNull()
        verify { processService.completeUserTask(aggregate, "4711") }
    }

    @Test
    fun `completeAlternativeClarification is not available while no clarification is open`() {

        // given: an aggregate that is not parked on the clarification user task
        knownAggregate()

        // when / then: the action is refused without touching the engine
        assertThatThrownBy { underTest.completeAlternativeClarification(application.id, alternativeFound = false) }
            .isInstanceOf(NoSuchElementException::class.java)
        confirmVerified(processService)
    }

    private fun knownAggregate() {
        every { aggregates.findById(application.id.value.toString()) } returns Optional.of(aggregate)
    }
}
