package io.miragon.blueprint.adapter.process

import com.ninjasquad.springmockk.MockkBean
import io.miragon.blueprint.adapter.outbound.dealer.BikeDealerAdapter.DealerUnavailableException
import io.miragon.blueprint.adapter.outbound.temporal.LeasingProcessAdapter
import io.miragon.blueprint.application.port.inbound.ActivateLeasingUseCase
import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase
import io.miragon.blueprint.application.port.inbound.CancelContractUseCase
import io.miragon.blueprint.application.port.inbound.CancelInsurancePolicyUseCase
import io.miragon.blueprint.application.port.inbound.IssueInsurancePolicyUseCase
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.miragon.blueprint.application.port.inbound.RejectApplicationUseCase
import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase
import io.miragon.blueprint.application.port.inbound.SendCancellationConfirmationUseCase
import io.miragon.blueprint.application.port.inbound.SendContractUseCase
import io.miragon.blueprint.application.port.inbound.SendSignatureReminderUseCase
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.bike.BikeUnavailableException
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.leasing.CustomerName
import io.miragon.blueprint.domain.leasing.Email
import io.miragon.blueprint.domain.leasing.LeasingApplication
import io.miragon.common.test.temporal.awaitActivitySettled
import io.miragon.common.test.temporal.awaitCancelRequested
import io.mockk.every
import io.mockk.verify
import io.mockk.verifyOrder
import io.temporal.api.enums.v1.IndexedValueType
import io.temporal.testing.TestWorkflowEnvironment
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Process test for the MiraVelo bike-leasing workflow on Temporal's in-memory test server. The workers
 * and activities come up as in production and the use cases are mocked, so we assert the *process*
 * behaviour. Signals and updates go through the real process adapter, and timers are skipped with
 * `env.sleep` or while the test waits for the result. Each test gets its own context and test server:
 * a case left open would hold back the time skipping of the next test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Timeout(60)
class BikeLeasingProcessTest {

    @Autowired
    private lateinit var env: TestWorkflowEnvironment

    @Autowired
    private lateinit var process: LeasingProcessAdapter

    @MockkBean(relaxed = true)
    private lateinit var rejectApplicationUseCase: RejectApplicationUseCase

    @MockkBean(relaxed = true)
    private lateinit var sendContractUseCase: SendContractUseCase

    @MockkBean(relaxed = true)
    private lateinit var sendSignatureReminderUseCase: SendSignatureReminderUseCase

    @MockkBean
    private lateinit var orderBikeUseCase: OrderBikeUseCase

    @MockkBean(relaxed = true)
    private lateinit var issueInsurancePolicyUseCase: IssueInsurancePolicyUseCase

    @MockkBean(relaxed = true)
    private lateinit var activateLeasingUseCase: ActivateLeasingUseCase

    @MockkBean(relaxed = true)
    private lateinit var cancelContractUseCase: CancelContractUseCase

    @MockkBean(relaxed = true)
    private lateinit var cancelInsurancePolicyUseCase: CancelInsurancePolicyUseCase

    @MockkBean(relaxed = true)
    private lateinit var sendCancellationConfirmationUseCase: SendCancellationConfirmationUseCase

    @MockkBean
    private lateinit var requestOrderCancellationUseCase: RequestOrderCancellationUseCase

    @MockkBean(relaxed = true)
    private lateinit var bookCancellationCostsUseCase: BookCancellationCostsUseCase

    private val id = ApplicationId.new()
    private val workflowId = id.value.toString()
    private val orderId = OrderId("ORDER-${UUID.randomUUID()}")

    @BeforeEach
    fun setUp() {
        env.registerSearchAttribute(CLARIFICATION_WAITING_SINCE.name, IndexedValueType.INDEXED_VALUE_TYPE_DATETIME)
        every { orderBikeUseCase.orderBike(any(), any()) } returns orderId
        every { requestOrderCancellationUseCase.requestCancellation(any()) } returns false
    }

    @Test
    fun `happy path - signed, ordered, insured, handed over, active after the withdrawal period`() {
        submit()
        process.correlateContractSigned(id)
        process.correlateHandoverReported(id)

        awaitCompletion()

        verifyOrder {
            sendContractUseCase.sendContract(id)
            orderBikeUseCase.orderBike(id, BIKE)
            activateLeasingUseCase.activate(id)
        }
        verify(exactly = 1) { issueInsurancePolicyUseCase.issuePolicy(id) }
        verify(exactly = 0) { sendSignatureReminderUseCase.sendSignatureReminder(id) }
        verify(exactly = 0) { sendCancellationConfirmationUseCase.sendCancellationConfirmation(id) }
    }

    @Test
    fun `withdrawal period - no activation before the 14 days elapsed`() {
        submit()
        process.correlateContractSigned(id)
        process.correlateHandoverReported(id)
        verify(timeout = TIMEOUT_MILLIS) { orderBikeUseCase.orderBike(id, BIKE) }
        awaitPolicySettled()

        env.sleep(Duration.ofDays(13))

        verify(exactly = 0) { activateLeasingUseCase.activate(id) }
    }

    @Test
    fun `not solvent - the credit rule routes the application straight to rejection`() {
        submit(age = 15)

        awaitCompletion()

        verify(exactly = 1) { rejectApplicationUseCase.reject(id) }
        verify(exactly = 0) { sendContractUseCase.sendContract(id) }
    }

    @Test
    fun `escalation - a contract not signed in time is rejected, without cancelling it`() {
        submit()

        awaitCompletion()

        verify(exactly = 1) { rejectApplicationUseCase.reject(id) }
        verify(exactly = 0) { cancelContractUseCase.cancelContract(id) }
        verify(exactly = 0) { orderBikeUseCase.orderBike(id, any()) }
    }

    @Test
    fun `reminder - sent on day 7, the leasing still goes through after the signature`() {
        submit()

        env.sleep(Duration.ofDays(6))
        verify(exactly = 0) { sendSignatureReminderUseCase.sendSignatureReminder(id) }
        env.sleep(Duration.ofDays(2))
        verify(timeout = TIMEOUT_MILLIS) { sendSignatureReminderUseCase.sendSignatureReminder(id) }

        process.correlateContractSigned(id)
        process.correlateHandoverReported(id)
        awaitCompletion()

        verify(exactly = 1) { sendSignatureReminderUseCase.sendSignatureReminder(id) }
        verify(exactly = 1) { activateLeasingUseCase.activate(id) }
    }

    @Test
    fun `alternative accepted - the chosen bike is ordered`() {
        every { orderBikeUseCase.orderBike(id, BIKE) } throws BikeUnavailableException(BIKE)
        every { orderBikeUseCase.orderBike(id, ALTERNATIVE_BIKE) } returns orderId
        submit()
        process.correlateContractSigned(id)
        process.correlateHandoverReported(id)

        clarify(alternativeFound = true, ALTERNATIVE_BIKE)
        awaitCompletion()

        verify(exactly = 1) { activateLeasingUseCase.activate(id) }
        verify(exactly = 0) { cancelContractUseCase.cancelContract(id) }
    }

    @Test
    fun `alternative unavailable - the customer is asked again`() {
        val secondAlternative = BikeId("BIKE-3")
        every { orderBikeUseCase.orderBike(id, BIKE) } throws BikeUnavailableException(BIKE)
        every { orderBikeUseCase.orderBike(id, ALTERNATIVE_BIKE) } throws BikeUnavailableException(ALTERNATIVE_BIKE)
        every { orderBikeUseCase.orderBike(id, secondAlternative) } returns orderId
        submit()
        process.correlateContractSigned(id)
        process.correlateHandoverReported(id)

        clarify(alternativeFound = true, ALTERNATIVE_BIKE)
        clarify(alternativeFound = true, secondAlternative)
        awaitCompletion()

        verify(exactly = 1) { activateLeasingUseCase.activate(id) }
    }

    @Test
    fun `alternative declined - contract and policy are reversed, no order is cancelled`() {
        every { orderBikeUseCase.orderBike(id, any()) } throws BikeUnavailableException(BIKE)
        submit()
        process.correlateContractSigned(id)

        clarify(alternativeFound = false)
        awaitCompletion()

        verify(exactly = 1) { cancelContractUseCase.cancelContract(id) }
        verify(exactly = 1) { cancelInsurancePolicyUseCase.cancelPolicy(id) }
        verify(exactly = 1) { sendCancellationConfirmationUseCase.sendCancellationConfirmation(id) }
        verify(exactly = 0) { requestOrderCancellationUseCase.requestCancellation(orderId) }
        verify(exactly = 0) { activateLeasingUseCase.activate(id) }
    }

    @Test
    fun `clarification - rejected when nobody asked for it`() {
        submit()

        assertThatThrownBy { process.completeAlternativeClarification(id, alternativeFound = false) }
            .isInstanceOf(NoSuchElementException::class.java)
    }

    @Test
    fun `withdrawal before signing - only the cancellation is confirmed`() {
        submit()
        verify(timeout = TIMEOUT_MILLIS) { sendContractUseCase.sendContract(id) }

        process.correlateApplicationWithdrawn(id)
        awaitCompletion()

        verify(exactly = 1) { sendCancellationConfirmationUseCase.sendCancellationConfirmation(id) }
        verify(exactly = 0) { cancelContractUseCase.cancelContract(id) }
        verify(exactly = 0) { rejectApplicationUseCase.reject(id) }
    }

    @Test
    fun `withdrawal after the order - contract, policy and order are reversed`() {
        submit()
        process.correlateContractSigned(id)
        verify(timeout = TIMEOUT_MILLIS) { orderBikeUseCase.orderBike(id, BIKE) }
        awaitPolicySettled()

        process.correlateApplicationWithdrawn(id)
        awaitCompletion()

        verify(exactly = 1) { cancelContractUseCase.cancelContract(id) }
        verify(exactly = 1) { cancelInsurancePolicyUseCase.cancelPolicy(id) }
        verify(exactly = 1) { requestOrderCancellationUseCase.requestCancellation(orderId) }
        verify(exactly = 1) { sendCancellationConfirmationUseCase.sendCancellationConfirmation(id) }
        verify(exactly = 0) { activateLeasingUseCase.activate(id) }
    }

    @Test
    fun `withdrawal in the withdrawal period - the activation does not happen`() {
        submit()
        process.correlateContractSigned(id)
        process.correlateHandoverReported(id)
        verify(timeout = TIMEOUT_MILLIS) { orderBikeUseCase.orderBike(id, BIKE) }
        awaitPolicySettled()
        env.sleep(Duration.ofDays(1))

        process.correlateApplicationWithdrawn(id)
        awaitCompletion()

        verify(exactly = 1) { requestOrderCancellationUseCase.requestCancellation(orderId) }
        verify(exactly = 0) { activateLeasingUseCase.activate(id) }
    }

    @Test
    fun `withdrawal while clarifying - the clarification closes, the contract is reversed`() {
        every { orderBikeUseCase.orderBike(id, any()) } throws BikeUnavailableException(BIKE)
        submit()
        process.correlateContractSigned(id)
        verify(timeout = TIMEOUT_MILLIS) { orderBikeUseCase.orderBike(id, BIKE) }
        awaitPolicySettled()

        process.correlateApplicationWithdrawn(id)
        awaitCompletion()

        verify(exactly = 1) { cancelContractUseCase.cancelContract(id) }
        verify(exactly = 1) { sendCancellationConfirmationUseCase.sendCancellationConfirmation(id) }
        verify(exactly = 0) { requestOrderCancellationUseCase.requestCancellation(orderId) }
    }

    @Test
    fun `withdrawal after an accepted alternative - that order is cancelled`() {
        every { orderBikeUseCase.orderBike(id, BIKE) } throws BikeUnavailableException(BIKE)
        every { orderBikeUseCase.orderBike(id, ALTERNATIVE_BIKE) } returns orderId
        submit()
        process.correlateContractSigned(id)
        clarify(alternativeFound = true, ALTERNATIVE_BIKE)
        verify(timeout = TIMEOUT_MILLIS) { orderBikeUseCase.orderBike(id, ALTERNATIVE_BIKE) }
        awaitPolicySettled()

        process.correlateApplicationWithdrawn(id)
        awaitCompletion()

        verify(exactly = 1) { requestOrderCancellationUseCase.requestCancellation(orderId) }
        verify(exactly = 1) { sendCancellationConfirmationUseCase.sendCancellationConfirmation(id) }
    }

    @Test
    fun `withdrawal while the order runs - the order completes and is cancelled`() {
        val orderRunning = CountDownLatch(1)
        val orderMayComplete = CountDownLatch(1)
        every { orderBikeUseCase.orderBike(id, any()) } answers {
            orderRunning.countDown()
            orderMayComplete.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            orderId
        }
        submit()
        process.correlateContractSigned(id)
        assertThat(orderRunning.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()
        awaitPolicySettled()

        process.correlateApplicationWithdrawn(id)
        env.awaitCancelRequested(workflowId)
        orderMayComplete.countDown()
        awaitCompletion()

        verify(exactly = 1) { requestOrderCancellationUseCase.requestCancellation(orderId) }
    }

    @Test
    fun `withdrawal while the policy is issued - the policy is issued and cancelled`() {
        val issuing = CountDownLatch(1)
        val issuingMayComplete = CountDownLatch(1)
        every { issueInsurancePolicyUseCase.issuePolicy(id) } answers {
            issuing.countDown()
            issuingMayComplete.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            Unit
        }
        submit()
        process.correlateContractSigned(id)
        assertThat(issuing.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()
        verify(timeout = TIMEOUT_MILLIS) { orderBikeUseCase.orderBike(id, BIKE) }
        env.awaitActivitySettled(workflowId, "OrderBike")

        process.correlateApplicationWithdrawn(id)
        env.awaitCancelRequested(workflowId)
        issuingMayComplete.countDown()
        awaitCompletion()

        verify(exactly = 1) { cancelInsurancePolicyUseCase.cancelPolicy(id) }
    }

    @Test
    fun `order cancellation possible - waits for the return clarification and books the costs`() {
        every { requestOrderCancellationUseCase.requestCancellation(orderId) } returns true
        submit()
        process.correlateContractSigned(id)
        verify(timeout = TIMEOUT_MILLIS) { orderBikeUseCase.orderBike(id, BIKE) }
        awaitPolicySettled()

        process.correlateApplicationWithdrawn(id)
        verify(timeout = TIMEOUT_MILLIS) { requestOrderCancellationUseCase.requestCancellation(orderId) }
        verify(exactly = 0) { bookCancellationCostsUseCase.bookCosts(orderId) }
        verify(exactly = 0) { sendCancellationConfirmationUseCase.sendCancellationConfirmation(id) }

        val cancellation = env.workflowClient.newWorkflowStub(
            CancelBikeOrderWorkflow::class.java,
            CancelBikeOrderWorkflow.workflowId(workflowId),
        )
        await().atMost(TIMEOUT).untilAsserted { cancellation.returnClarified() }
        awaitCompletion()

        verifyOrder {
            bookCancellationCostsUseCase.bookCosts(orderId)
            sendCancellationConfirmationUseCase.sendCancellationConfirmation(id)
        }
    }

    @Test
    fun `order cancellation not possible - ends without booking costs`() {
        submit()
        process.correlateContractSigned(id)
        verify(timeout = TIMEOUT_MILLIS) { orderBikeUseCase.orderBike(id, BIKE) }
        awaitPolicySettled()

        process.correlateApplicationWithdrawn(id)
        awaitCompletion()

        verify(exactly = 0) { bookCancellationCostsUseCase.bookCosts(orderId) }
        verify(exactly = 1) { sendCancellationConfirmationUseCase.sendCancellationConfirmation(id) }
    }

    @Test
    fun `dealer outage - retries every 10 seconds until the dealer recovers`() {
        var attempts = 0
        every { orderBikeUseCase.orderBike(id, any()) } answers {
            if (++attempts < 4) throw DealerUnavailableException(BIKE)
            orderId
        }
        submit()
        val started = env.currentTimeMillis()
        process.correlateContractSigned(id)
        process.correlateHandoverReported(id)

        awaitCompletion()

        assertThat(attempts).isEqualTo(4)
        assertThat(env.currentTimeMillis() - started).isGreaterThanOrEqualTo(Duration.ofSeconds(30).toMillis())
        verify(exactly = 1) { activateLeasingUseCase.activate(id) }
    }

    @Test
    fun `dealer outage - stays withdrawable and leaves no order behind`() {
        every { orderBikeUseCase.orderBike(id, any()) } throws DealerUnavailableException(BIKE)
        submit()
        process.correlateContractSigned(id)
        verify(timeout = TIMEOUT_MILLIS) { orderBikeUseCase.orderBike(id, BIKE) }
        awaitPolicySettled()

        process.correlateApplicationWithdrawn(id)
        awaitCompletion()

        verify(exactly = 1) { sendCancellationConfirmationUseCase.sendCancellationConfirmation(id) }
        verify(exactly = 0) { requestOrderCancellationUseCase.requestCancellation(orderId) }
    }

    private fun submit(age: Int = 35) {
        process.submitRequest(
            LeasingApplication.receive(
                id = id,
                customerName = CustomerName("Max Mustermann"),
                email = Email("max@example.com"),
                age = age,
                monthlyNetIncome = 3500.0,
                bikeId = BIKE,
                createdAt = LocalDateTime.now(),
            ),
        )
    }

    private fun clarify(alternativeFound: Boolean, bikeId: BikeId? = null) {
        await().atMost(TIMEOUT).ignoreException(NoSuchElementException::class.java).untilAsserted {
            process.completeAlternativeClarification(id, alternativeFound, bikeId)
        }
    }

    private fun awaitPolicySettled() {
        verify(timeout = TIMEOUT_MILLIS) { issueInsurancePolicyUseCase.issuePolicy(id) }
        env.awaitActivitySettled(workflowId, "IssueInsurancePolicy")
    }

    private fun awaitCompletion() {
        env.workflowClient.newUntypedWorkflowStub(workflowId).getResult(Void::class.java)
    }

    private companion object {
        val BIKE = BikeId("BIKE-1")
        val ALTERNATIVE_BIKE = BikeId("BIKE-2")
        const val TIMEOUT_MILLIS = 10_000L
        val TIMEOUT: Duration = Duration.ofMillis(TIMEOUT_MILLIS)
    }
}
