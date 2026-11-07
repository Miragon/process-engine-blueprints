package io.miragon.blueprint.application.service

import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository
import io.miragon.blueprint.application.port.outbound.LeasingProcess
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.leasing.LeasingStatus
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import io.mockk.Runs
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class ClarifyReturnServiceTest {

    private val repository = mockk<LeasingApplicationRepository>()
    private val process = mockk<LeasingProcess>()
    private val underTest = ClarifyReturnService(repository = repository, process = process)

    @Test
    fun `clarifyReturn completes the return clarification of a known application`() {

        // given: a withdrawn application whose order the dealer could not cancel
        val id = ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
        every { repository.findById(id) } returns testLeasingApplication(id = id, status = LeasingStatus.WITHDRAWN)
        every { process.completeReturnClarification(id) } just Runs

        // when: the back office confirms the return
        underTest.clarifyReturn(id)

        // then: the return clarification is completed in the process
        verify { repository.findById(id) }
        verify { process.completeReturnClarification(id) }
        confirmVerified(repository, process)
    }

    @Test
    fun `clarifyReturn fails for an unknown application`() {

        // given: an application id the repository does not know
        val unknownId = ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174999"))
        every { repository.findById(unknownId) } returns null

        // when / then: the lookup fails and the process is not touched
        assertThatThrownBy { underTest.clarifyReturn(unknownId) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Unknown application $unknownId")
        verify { repository.findById(unknownId) }
        confirmVerified(repository, process)
    }
}
