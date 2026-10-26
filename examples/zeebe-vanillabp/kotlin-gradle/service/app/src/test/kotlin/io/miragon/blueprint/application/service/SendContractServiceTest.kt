package io.miragon.blueprint.application.service

import io.miragon.blueprint.application.port.outbound.ContractPort
import io.miragon.blueprint.application.port.outbound.NotificationPort
import io.miragon.blueprint.domain.leasing.ContractId
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import io.mockk.Runs
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SendContractServiceTest {

    private val contract = mockk<ContractPort>()
    private val notification = mockk<NotificationPort>()
    private val underTest = SendContractService(contract = contract, notification = notification)

    @Test
    fun `sendContract issues the contract, records its id on the application and notifies the customer`() {

        // given: an application whose contract the contract system will issue
        val application = testLeasingApplication()
        every { contract.issueContract(application.id) } returns ContractId("CONTRACT-1")
        every { notification.send(any(), any()) } just Runs

        // when: the contract is sent
        val contracted = underTest.sendContract(application)

        // then: the contract is issued, its id is recorded on the application and the customer is asked to sign
        assertThat(contracted).isEqualTo(application.copy(contractId = ContractId("CONTRACT-1")))
        verify { contract.issueContract(application.id) }
        verify { notification.send("Please review and sign your leasing contract", application) }
        confirmVerified(contract, notification)
    }

    @Test
    fun `sendContract neither issues nor announces a second contract when the task is delivered again`() {

        // given: an application that already carries the contract of an earlier delivery
        val application = testLeasingApplication(contractId = ContractId("CONTRACT-1"))

        // when: the contract is sent again
        val contracted = underTest.sendContract(application)

        // then: the application is untouched and no outbound system is called
        assertThat(contracted).isEqualTo(application)
        confirmVerified(contract, notification)
    }
}
