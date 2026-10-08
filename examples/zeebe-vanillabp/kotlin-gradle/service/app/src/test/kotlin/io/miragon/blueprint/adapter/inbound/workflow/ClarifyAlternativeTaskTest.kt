package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import io.vanillabp.spi.service.TaskEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class ClarifyAlternativeTaskTest {

    private val clock = Clock.fixed(Instant.parse("2026-03-01T08:00:00Z"), ZoneOffset.UTC)
    private val task = ClarifyAlternativeTask(clock)

    @Test
    fun `remembers the created user task on the workflow aggregate`() {
        // given
        val aggregate = LeasingApplicationAggregate.of(testLeasingApplication())

        // when
        task.clarifyAlternative(aggregate, "4711", TaskEvent.Event.CREATED)

        // then
        assertThat(aggregate.clarifyAlternativeTaskId).isEqualTo("4711")
        assertThat(aggregate.clarificationRequestedAt).isEqualTo(LocalDateTime.of(2026, 3, 1, 8, 0))
    }

    @Test
    fun `forgets the user task when the engine cancels it`() {
        // given
        val aggregate = LeasingApplicationAggregate.of(testLeasingApplication())
        task.clarifyAlternative(aggregate, "4711", TaskEvent.Event.CREATED)

        // when
        task.clarifyAlternative(aggregate, "4711", TaskEvent.Event.CANCELED)

        // then
        assertThat(aggregate.clarifyAlternativeTaskId).isNull()
        assertThat(aggregate.clarificationRequestedAt).isNull()
    }
}
