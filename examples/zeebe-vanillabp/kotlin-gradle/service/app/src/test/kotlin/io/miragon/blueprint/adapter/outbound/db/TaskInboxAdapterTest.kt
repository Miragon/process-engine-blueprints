package io.miragon.blueprint.adapter.outbound.db

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.outbound.TaskInboxPort
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.UUID

@DataJpaTest
@ActiveProfiles("test")
@Import(TaskInboxAdapter::class)
class TaskInboxAdapterTest {

    @Autowired
    private lateinit var underTest: TaskInboxAdapter

    @Autowired
    private lateinit var entityManager: TestEntityManager

    @Test
    fun `lists the applications parked on an open clarification with the time they started waiting`() {

        // given: one application waiting for a clarification, one whose clarification was resolved and one untouched
        val waiting = aggregate()
        waiting.clarificationRequested("4711", LocalDateTime.of(2026, 3, 1, 8, 0))
        val resolved = aggregate()
        resolved.clarificationRequested("4712", LocalDateTime.of(2026, 3, 2, 8, 0))
        resolved.resolveClarification(alternativeFound = true)
        listOf(waiting, resolved, aggregate()).forEach { entityManager.persist(it) }
        entityManager.flush()
        entityManager.clear()

        // when: the inbox is read
        val openClarifications = underTest.findOpenClarifications()

        // then: only the waiting application is listed
        assertThat(openClarifications).containsExactly(
            TaskInboxPort.OpenClarification(
                applicationId = ApplicationId.of(waiting.applicationId),
                waitingSince = LocalDateTime.of(2026, 3, 1, 8, 0),
            ),
        )
    }

    @Test
    fun `the inbox is empty while no clarification is open`() {

        // given: an application that never needed a clarification
        entityManager.persist(aggregate())
        entityManager.flush()
        entityManager.clear()

        // when / then: nothing is listed
        assertThat(underTest.findOpenClarifications()).isEmpty()
    }

    private fun aggregate() =
        LeasingApplicationAggregate.of(testLeasingApplication(id = ApplicationId(UUID.randomUUID())))
}
