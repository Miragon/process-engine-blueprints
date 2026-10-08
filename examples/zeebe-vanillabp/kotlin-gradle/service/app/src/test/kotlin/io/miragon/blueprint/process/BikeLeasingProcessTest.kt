package io.miragon.blueprint.process

import com.ninjasquad.springmockk.MockkBean
import io.camunda.client.CamundaClient
import io.camunda.client.api.search.enums.UserTaskState
import io.camunda.process.test.api.CamundaAssert
import io.camunda.process.test.api.CamundaProcessTestContext
import io.camunda.process.test.api.CamundaSpringProcessTest
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors
import io.miragon.blueprint.adapter.outbound.db.LeasingApplicationJpaRepository
import io.miragon.blueprint.adapter.outbound.workflow.LeasingProcessAdapter
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes
import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi
import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
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
import io.miragon.blueprint.application.port.inbound.ValidateApplicationUseCase
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.leasing.ApplicationInvalidException
import io.miragon.blueprint.domain.leasing.ContractId
import io.miragon.blueprint.domain.leasing.CustomerName
import io.miragon.blueprint.domain.leasing.Email
import io.miragon.blueprint.domain.leasing.LeasingApplication
import io.miragon.blueprint.domain.leasing.LeasingStatus
import io.miragon.bpmn.runtime.path.ProcessPath
import io.miragon.bpmn.runtime.path.enter
import io.miragon.bpmn.runtime.path.inside
import io.miragon.bpmn.runtime.path.onto
import io.miragon.bpmn.runtime.path.then
import io.miragon.bpmn.runtime.path.throwingCompensation
import io.miragon.common.test.assertions.hasCompletedElements
import io.miragon.common.test.assertions.hasCompletedElementsInOrder
import io.miragon.common.test.config.WorkflowDeploymentTestConfiguration
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.LocalDateTime

/**
 * Process test for the MiraVelo Bike-Leasing process on a real (in-container) Camunda 8 engine.
 * `@CamundaSpringProcessTest` starts the engine, VanillaBP deploys the models and binds the
 * `@WorkflowTask` methods, and the use cases are mocked so we assert the *process* behaviour together
 * with what VanillaBP persists on the workflow aggregate. Because Zeebe drives itself asynchronously,
 * `CamundaAssert` (which polls until a timeout) doubles as the synchronisation mechanism: asserting a
 * wait-state element has completed is how we wait for it. Timers are advanced with
 * `processTestContext.increaseTime` and messages and the clarification user task go through the real
 * process adapter, inside a transaction like the application services provide.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@CamundaSpringProcessTest
@ActiveProfiles("test")
@Import(WorkflowDeploymentTestConfiguration::class)
class BikeLeasingProcessTest {

    @Autowired
    private lateinit var camundaClient: CamundaClient

    @Autowired
    private lateinit var processTestContext: CamundaProcessTestContext

    @Autowired
    private lateinit var process: LeasingProcessAdapter

    @Autowired
    private lateinit var aggregates: LeasingApplicationJpaRepository

    @Autowired
    private lateinit var transaction: TransactionTemplate

    @MockkBean(relaxed = true)
    private lateinit var validateApplicationUseCase: ValidateApplicationUseCase

    @MockkBean
    private lateinit var rejectApplicationUseCase: RejectApplicationUseCase

    @MockkBean
    private lateinit var sendContractUseCase: SendContractUseCase

    @MockkBean(relaxed = true)
    private lateinit var cancelContractUseCase: CancelContractUseCase

    @MockkBean(relaxed = true)
    private lateinit var issueInsurancePolicyUseCase: IssueInsurancePolicyUseCase

    @MockkBean(relaxed = true)
    private lateinit var cancelInsurancePolicyUseCase: CancelInsurancePolicyUseCase

    @MockkBean(relaxed = true)
    private lateinit var sendSignatureReminderUseCase: SendSignatureReminderUseCase

    @MockkBean
    private lateinit var sendCancellationConfirmationUseCase: SendCancellationConfirmationUseCase

    @MockkBean(relaxed = true)
    private lateinit var requestOrderCancellationUseCase: RequestOrderCancellationUseCase

    @MockkBean(relaxed = true)
    private lateinit var bookCancellationCostsUseCase: BookCancellationCostsUseCase

    @MockkBean
    private lateinit var orderBikeUseCase: OrderBikeUseCase

    @MockkBean
    private lateinit var activateLeasingUseCase: ActivateLeasingUseCase

    @BeforeEach
    fun setUp() {
        every { sendContractUseCase.sendContract(any()) } answers
            { firstArg<LeasingApplication>().withContract(ContractId("CONTRACT-1")) }
        every { orderBikeUseCase.orderBike(any()) } answers
            { firstArg<LeasingApplication>().documentOrder(OrderId("ORDER-1")) }
        every { activateLeasingUseCase.activate(any()) } answers { firstArg<LeasingApplication>().activate() }
        every { rejectApplicationUseCase.reject(any()) } answers { firstArg<LeasingApplication>().reject() }
        every { sendCancellationConfirmationUseCase.sendCancellationConfirmation(any()) } answers
            { firstArg<LeasingApplication>().cancel() }
    }

    @Test
    fun `happy path - contract signed, bike available, leasing becomes active`() {
        val id = submit(age = 35, income = 3500.0)
        val instanceKey = awaitProcessInstance()
        val instance = ProcessInstanceSelectors.byKey(instanceKey)

        // wait until the contract was sent and the process parks on the await-signature gateway
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.ServiceTaskSendContract)
        assertThat(aggregateOf(id).contractId).isEqualTo("CONTRACT-1")
        inTransaction { process.correlateContractSigned(id) }

        // the fork runs insurance + bike order, then joins and parks on the handover message
        CamundaAssert.assertThatProcessInstance(instance)
            .hasCompletedElements(FlowNodes.ServiceTaskIssueInsurancePolicy, FlowNodes.ServiceTaskOrderBike)
        inTransaction { process.correlateHandoverReported(id) }

        // the 14-day withdrawal period elapses
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.EventHandoverReported)
        processTestContext.increaseTime(Duration.ofDays(14).plusHours(1))

        CamundaAssert.assertThatProcessInstance(instance).isCompleted()
        CamundaAssert.assertThatProcessInstance(instance)
            .hasCompletedElementsInOrder(
                pathUntilContractSigned()
                    .then { it.gatewayFork }
                    .then { it.serviceTaskIssueInsurancePolicy }
                    .then { it.gatewayJoin }
                    .then { it.eventHandoverReported }
                    .then { it.eventWithdrawalPeriodElapsed }
                    .then { it.serviceTaskActivateLeasing }
                    .then { it.endEventLeasingActive },
            )
            .hasCompletedElementsInOrder(
                ProcessPath.from(FlowNodes.GatewayFork)
                    .then { it.gatewayBikeSourceJoin }
                    .then { it.serviceTaskOrderBike }
                    .then { it.gatewayBikeAvailable }
                    .then { it.gatewayJoin },
            )
        verify(exactly = 1) { sendContractUseCase.sendContract(match { it.id == id }) }
        verify(exactly = 1) { issueInsurancePolicyUseCase.issuePolicy(id) }
        verify(exactly = 1) { activateLeasingUseCase.activate(match { it.id == id }) }

        // the workflow aggregate, reloaded from the database, carries everything the tasks changed
        val aggregate = aggregateOf(id)
        assertThat(aggregate.status).isEqualTo(LeasingStatus.ACTIVE)
        assertThat(aggregate.orderId).isEqualTo("ORDER-1")
        assertThat(aggregate.contractId).isEqualTo("CONTRACT-1")
    }

    @Test
    fun `the engine only ever sees the routing values the workflow aggregate releases`() {
        val id = submit(age = 35, income = 3500.0)
        val instanceKey = awaitProcessInstance()
        val instance = ProcessInstanceSelectors.byKey(instanceKey)

        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.ServiceTaskSendContract)
        inTransaction { process.correlateContractSigned(id) }
        CamundaAssert.assertThatProcessInstance(instance)
            .hasCompletedElements(FlowNodes.ServiceTaskIssueInsurancePolicy, FlowNodes.ServiceTaskOrderBike)

        val variables = awaitVariable(instanceKey, name = "bikeAvailable", value = "true")
        assertThat(variables.map { it.first })
            .containsOnly("applicationId", "age", "monthlyNetIncome", "solvent", "bikeAvailable")
        assertThat(variables).contains("applicationId" to "\"${id.value}\"")
    }

    @Test
    fun `escalation - contract not signed in time is escalated and rejected`() {
        val id = submit(age = 35, income = 3500.0)
        val instanceKey = awaitProcessInstance()
        val instance = ProcessInstanceSelectors.byKey(instanceKey)

        // park on the await-signature gateway, then let the 14-day signature deadline elapse
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.ServiceTaskSendContract)
        processTestContext.increaseTime(Duration.ofDays(14).plusHours(1))

        CamundaAssert.assertThatProcessInstance(instance).isCompleted()
        // Zeebe terminates the throwing end event and its sub-process, so the walk resumes at the boundary event
        CamundaAssert.assertThatProcessInstance(instance)
            .hasCompletedElementsInOrder(pathUntilSignatureAwaited().then { it.eventSignatureDeadline })
            .hasTerminatedElements(
                FlowNodes.EndEventNotSigned.ELEMENT_ID,
                FlowNodes.SubProcessConcludeContract.ELEMENT_ID,
            )
            .hasCompletedElementsInOrder(
                ProcessPath.from(FlowNodes.EventContractNotSigned)
                    .then { it.gatewayRejectionJoin }
                    .then { it.serviceTaskSendRejection }
                    .then { it.endEventApplicationRejected },
            )
        verify(exactly = 1) { rejectApplicationUseCase.reject(match { it.id == id }) }
        assertThat(aggregateOf(id).status).isEqualTo(LeasingStatus.REJECTED)
    }

    @Test
    fun `not solvent - the DMN routes the application straight to rejection`() {
        // age below 18 cannot sign a leasing contract, so the DMN returns solvent = false
        val id = submit(age = 15, income = 3500.0)
        val instanceKey = awaitProcessInstance()
        val instance = ProcessInstanceSelectors.byKey(instanceKey)

        CamundaAssert.assertThatProcessInstance(instance).isCompleted()
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElementsInOrder(
            pathUntilCreditRatingChecked()
                .then { it.gatewayRejectionJoin }
                .then { it.serviceTaskSendRejection }
                .then { it.endEventApplicationRejected },
        )
        verify(exactly = 1) { rejectApplicationUseCase.reject(match { it.id == id }) }
        verify(exactly = 0) { sendContractUseCase.sendContract(any()) }
    }

    @Test
    fun `invalid application - the BPMN error routes the application to rejection`() {
        every { validateApplicationUseCase.validate(any()) } answers
            { throw ApplicationInvalidException(firstArg<LeasingApplication>().id, "no income") }

        val id = submit(age = 35, income = 0.0)
        val instanceKey = awaitProcessInstance()
        val instance = ProcessInstanceSelectors.byKey(instanceKey)

        CamundaAssert.assertThatProcessInstance(instance).isCompleted()
        // Zeebe terminates the task that threw the BPMN error, so the walk starts at the boundary event
        CamundaAssert.assertThatProcessInstance(instance)
            .hasTerminatedElements(FlowNodes.ServiceTaskValidateApplication.ELEMENT_ID)
            .hasCompletedElementsInOrder(
                ProcessPath.from(FlowNodes.EventApplicationInvalid)
                    .then { it.gatewayRejectionJoin }
                    .then { it.serviceTaskSendRejection }
                    .then { it.endEventApplicationRejected },
            )
        verify(exactly = 1) { rejectApplicationUseCase.reject(match { it.id == id }) }
        verify(exactly = 0) { sendContractUseCase.sendContract(any()) }
        assertThat(aggregateOf(id).status).isEqualTo(LeasingStatus.REJECTED)
    }

    @Test
    fun `abort - withdrawing the application compensates the completed steps`() {
        every { requestOrderCancellationUseCase.requestCancellation(any()) } returns true

        val id = submit(age = 35, income = 3500.0)
        val instanceKey = awaitProcessInstance()
        val instance = ProcessInstanceSelectors.byKey(instanceKey)

        // drive to the handover wait state (contract signed, bike ordered, insured)
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.ServiceTaskSendContract)
        inTransaction { process.correlateContractSigned(id) }
        CamundaAssert.assertThatProcessInstance(instance)
            .hasCompletedElements(FlowNodes.ServiceTaskIssueInsurancePolicy, FlowNodes.ServiceTaskOrderBike)

        // withdraw -> compensation. The cancelBikeOrder call activity runs in its own (child)
        // process instance and parks on its clarify-return task, so we look it up by element id only.
        inTransaction { process.correlateApplicationWithdrawn(id) }
        awaitUserTaskCreated(CancelBikeOrderProcessApi.FlowNodes.UserTaskClarifyReturn.ELEMENT_ID)
        processTestContext.completeUserTask(CancelBikeOrderProcessApi.FlowNodes.UserTaskClarifyReturn.ELEMENT_ID)

        CamundaAssert.assertThatProcessInstance(instance).isCompleted()
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(
            ProcessPath.from(FlowNodes.StartEventApplicationWithdrawn)
                .then { it.eventReverseApplication }
                .throwingCompensation(FlowNodes.EventCompensateContract) { it.serviceTaskCancelContract }
                .throwingCompensation(FlowNodes.EventCompensateInsurance) { it.serviceTaskCancelPolicy }
                .throwingCompensation(FlowNodes.EventCompensateOrder) { it.callActivityCancelBikeOrder }
                .then { it.serviceTaskSendCancellationConfirmation }
                .then { it.endEventApplicationCancelled },
        )
        verify(exactly = 1) { cancelContractUseCase.cancelContract(match { it.id == id }) }
        verify(exactly = 1) { cancelInsurancePolicyUseCase.cancelPolicy(id) }
        verify(exactly = 1) { requestOrderCancellationUseCase.requestCancellation(OrderId("ORDER-1")) }
        verify(exactly = 1) { bookCancellationCostsUseCase.bookCosts(OrderId("ORDER-1")) }
        verify(exactly = 1) {
            sendCancellationConfirmationUseCase.sendCancellationConfirmation(match { it.id == id })
        }

        // the tasks of the called process worked on the very same workflow aggregate
        val aggregate = aggregateOf(id)
        assertThat(aggregate.status).isEqualTo(LeasingStatus.CANCELLED)
        assertThat(aggregate.cancellationPossible).isTrue()
    }

    @Test
    fun `bike unavailable - clarifying an alternative re-orders and leasing becomes active`() {
        // the first order finds the requested bike unavailable; the re-order after the alternative succeeds
        every { orderBikeUseCase.orderBike(any()) } answers
            { firstArg<LeasingApplication>() } andThenAnswer
            { firstArg<LeasingApplication>().documentOrder(OrderId("ORDER-2")) }

        val id = submit(age = 35, income = 3500.0)
        val instanceKey = awaitProcessInstance()
        val instance = ProcessInstanceSelectors.byKey(instanceKey)

        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.ServiceTaskSendContract)
        inTransaction { process.correlateContractSigned(id) }

        // the first order finds nothing -> parks on the clarify-alternative user task, which
        // VanillaBP reports to the workflow aggregate
        awaitUserTaskCreated(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID, instanceKey)
        val parked = aggregateOf(id)
        assertThat(parked.clarifyAlternativeTaskId).isNotNull()
        assertThat(parked.clarificationRequestedAt).isNotNull()
        inTransaction { process.completeAlternativeClarification(id, alternativeFound = true) }

        // re-order succeeds -> join -> handover -> withdrawal period
        CamundaAssert.assertThatProcessInstance(instance)
            .hasCompletedElements(FlowNodes.UserTaskClarifyAlternative)
        inTransaction { process.correlateHandoverReported(id) }
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.EventHandoverReported)
        processTestContext.increaseTime(Duration.ofDays(14).plusHours(1))

        CamundaAssert.assertThatProcessInstance(instance).isCompleted()
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElementsInOrder(
            ProcessPath.from(FlowNodes.GatewayFork)
                .then { it.gatewayBikeSourceJoin }
                .then { it.serviceTaskOrderBike }
                .then { it.gatewayBikeAvailable }
                .then { it.userTaskClarifyAlternative }
                .then { it.gatewayAlternativeFound }
                .then { it.gatewayBikeSourceJoin }
                .then { it.serviceTaskOrderBike }
                .then { it.gatewayBikeAvailable }
                .then { it.gatewayJoin }
                .then { it.eventHandoverReported }
                .then { it.eventWithdrawalPeriodElapsed }
                .then { it.serviceTaskActivateLeasing }
                .then { it.endEventLeasingActive },
        )
        verify(exactly = 2) { orderBikeUseCase.orderBike(match { it.id == id }) }

        // the clarification is closed on the reloaded workflow aggregate and the re-order is recorded
        val aggregate = aggregateOf(id)
        assertThat(aggregate.clarifyAlternativeTaskId).isNull()
        assertThat(aggregate.alternativeFound).isTrue()
        assertThat(aggregate.orderId).isEqualTo("ORDER-2")
        assertThat(aggregate.status).isEqualTo(LeasingStatus.ACTIVE)
    }

    @Test
    fun `dealer outage - the order retries count down and the engine raises an incident`() {
        every { orderBikeUseCase.orderBike(any()) } throws IllegalStateException("Bike dealer API unavailable")

        val id = submit(age = 35, income = 3500.0)
        val instanceKey = awaitProcessInstance()
        val instance = ProcessInstanceSelectors.byKey(instanceKey)

        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.ServiceTaskSendContract)
        inTransaction { process.correlateContractSigned(id) }

        // each failed attempt costs one of the three retries modelled on the task; the engine clock is
        // pushed past the modelled 10s backoff so the countdown does not slow the test down
        Awaitility.await()
            .atMost(Duration.ofSeconds(60))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted {
                processTestContext.increaseTime(Duration.ofSeconds(10))
                val incidents = camundaClient.newIncidentSearchRequest()
                    .filter { it.processInstanceKey(instanceKey) }
                    .send()
                    .join()
                    .items()
                assertThat(incidents).isNotEmpty()
            }

        verify(exactly = 3) { orderBikeUseCase.orderBike(match { it.id == id }) }
        CamundaAssert.assertThatProcessInstance(instance).isActive()
        assertThat(aggregateOf(id).orderId).isNull()
    }

    private fun pathUntilCreditRatingChecked() =
        ProcessPath.from(FlowNodes.StartEventLeasingRequestReceived)
            .then { it.serviceTaskValidateApplication }
            .then { it.businessRuleTaskCheckCreditRating }
            .then { it.gatewayIsSolvent }

    private fun pathUntilSignatureAwaited() =
        pathUntilCreditRatingChecked()
            .onto { it.subProcessConcludeContract }
            .enter { it.startEventCustomerEligible }
            .then { it.serviceTaskSendContract }
            .then { it.gatewayAwaitSignature }

    private fun pathUntilContractSigned() =
        pathUntilCreditRatingChecked()
            .onto { it.subProcessConcludeContract }
            .inside {
                enter { it.startEventCustomerEligible }
                    .then { it.serviceTaskSendContract }
                    .then { it.gatewayAwaitSignature }
                    .then { it.eventContractSigned }
                    .then { it.endEventContractValid }
            }

    private fun submit(age: Int, income: Double, bikeId: String = "BIKE-TEST"): ApplicationId {
        val application =
            LeasingApplication(
                id = ApplicationId.new(),
                customerName = CustomerName("Test Customer"),
                email = Email("test@example.com"),
                age = age,
                monthlyNetIncome = income,
                bikeId = BikeId(bikeId),
                status = LeasingStatus.RECEIVED,
                createdAt = LocalDateTime.now(),
            )
        inTransaction { process.submitRequest(application) }
        return application.id
    }

    private fun inTransaction(action: () -> Unit) {
        transaction.executeWithoutResult { action() }
    }

    private fun aggregateOf(id: ApplicationId): LeasingApplicationAggregate =
        aggregates.findById(id.value.toString()).orElseThrow()

    private fun awaitVariable(processInstanceKey: Long, name: String, value: String): List<Pair<String, String>> {
        var variables = emptyList<Pair<String, String>>()
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(200))
            .untilAsserted {
                variables = camundaClient.newVariableSearchRequest()
                    .filter { it.processInstanceKey(processInstanceKey) }
                    .send()
                    .join()
                    .items()
                    .map { it.name to it.value }
                assertThat(variables).contains(name to value)
            }
        return variables
    }

    private fun awaitProcessInstance(): Long {
        var instanceKey = 0L
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(200))
            .untilAsserted {
                val instances = camundaClient.newProcessInstanceSearchRequest()
                    .filter { it.processDefinitionId(BikeLeasingProcessProcessApi.PROCESS_ID.value) }
                    .send()
                    .join()
                    .items()
                assert(instances.isNotEmpty()) { "no bike-leasing process instance was started" }
                instanceKey = instances.first().processInstanceKey
            }
        return instanceKey
    }

    private fun awaitUserTaskCreated(elementId: String, processInstanceKey: Long? = null) {
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(200))
            .untilAsserted {
                val tasks = camundaClient.newUserTaskSearchRequest()
                    .filter {
                        it.elementId(elementId)
                        it.state(UserTaskState.CREATED)
                        if (processInstanceKey != null) it.processInstanceKey(processInstanceKey)
                    }
                    .send()
                    .join()
                    .items()
                assert(tasks.isNotEmpty()) { "user task '$elementId' was not created" }
            }
    }
}
