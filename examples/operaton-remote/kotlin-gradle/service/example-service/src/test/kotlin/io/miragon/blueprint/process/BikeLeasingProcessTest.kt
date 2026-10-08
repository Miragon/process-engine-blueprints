package io.miragon.blueprint.process

import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.FlowNodes
import io.miragon.bpmn.runtime.path.ProcessPath
import io.miragon.bpmn.runtime.path.enter
import io.miragon.bpmn.runtime.path.inside
import io.miragon.bpmn.runtime.path.interruptedBy
import io.miragon.bpmn.runtime.path.onto
import io.miragon.bpmn.runtime.path.then
import io.miragon.bpmn.runtime.path.throwingCompensation
import io.miragon.blueprint.process.Messages
import io.miragon.blueprint.process.ServiceTasks
import org.operaton.bpm.engine.ProcessEngine
import org.operaton.bpm.engine.delegate.ExecutionListener
import org.operaton.bpm.engine.delegate.TaskListener
import org.operaton.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration
import org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat
import org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.init
import org.operaton.bpm.engine.impl.mock.MockExpressionManager
import org.operaton.bpm.engine.impl.mock.Mocks
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Process-model behaviour test for the `bike-leasing` model — it lives with the **process owner**
 * (this service), which also owns and deploys the model. Because the worker embeds no engine, the test
 * spins up a fresh **standalone in-memory engine** per test and deploys the model from this service's
 * own resources.
 *
 * The service tasks are **external tasks**, so the test completes each one explicitly by topic via
 * [completeExternalTask] (supplying the output variables a real worker would return); user tasks,
 * messages and timers are released explicitly. It therefore verifies the *topology* — routing,
 * gateways, compensation, the event sub-process, the DMN and the timers — independent of what the
 * workers actually do with each task.
 */
class BikeLeasingProcessTest {

    private lateinit var engine: ProcessEngine

    /** Output of the `orderBike` external task when the requested bike is available. */
    private val bikeAvailable = mapOf(
        FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.value to "ORDER-1",
        FlowNodes.ServiceTaskOrderBike.Variables.BIKE_AVAILABLE.value to true,
    )

    @BeforeEach
    fun setUp() {
        // The job executor stays off by default, so the test drives async continuations by hand.
        // The model references two listeners by expression (`#{bikeOrderAuditListener}`,
        // `#{clarifyAlternativeTaskListener}`) whose real beans live in the `engine-service` — listeners
        // run inside the engine and have no external-task equivalent, so they cannot be worker beans.
        // The standalone test engine has no Spring context, so a `MockExpressionManager` + `Mocks`
        // registers no-op stand-ins under those bean names, letting the topology test resolve (and thus
        // verify) the `delegateExpression`s without pulling in the engine module.
        engine = StandaloneInMemProcessEngineConfiguration().apply {
            jdbcUrl = "jdbc:h2:mem:bikeleasing-process-${UUID.randomUUID()};DB_CLOSE_DELAY=1000"
            expressionManager = MockExpressionManager()
        }.buildProcessEngine()

        Mocks.register("bikeOrderAuditListener", ExecutionListener { /* stand-in: real bean in engine-service */ })
        Mocks.register("clarifyAlternativeTaskListener", TaskListener { /* stand-in: real bean in engine-service */ })

        // The process owner deploys its own model — here from this service's own resources on the classpath.
        engine.repositoryService.createDeployment()
            .addClasspathResource("bpmn/bike-leasing.bpmn")
            .addClasspathResource("bpmn/cancel-bike-order.bpmn")
            .addClasspathResource("dmn/check-credit-rating.dmn")
            .deploy()

        init(engine)
    }

    @AfterEach
    fun tearDown() {
        Mocks.reset()
        engine.close()
    }

    @Test
    fun `happy path - contract signed, bike available, leasing becomes active`() {
        val businessKey = submit(age = 35, income = 3500.0)
        val instance = engine.findInstance(businessKey)

        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_VALIDATE_APPLICATION) // -> DMN (solvent) -> sendContract
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_SEND_CONTRACT) // -> signature wait state

        engine.correlateMessage(Messages.MIRAVELO_CONTRACT_SIGNED, businessKey) // forks into insurance + bike order
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_ISSUE_INSURANCE_POLICY)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_ORDER_BIKE, bikeAvailable) // joins -> handover wait state

        engine.correlateMessage(Messages.MIRAVELO_HANDOVER_REPORTED, businessKey) // -> withdrawal-period timer
        engine.fireTimer(FlowNodes.EventWithdrawalPeriodElapsed)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_ACTIVATE_LEASING) // flips read model to ACTIVE -> end

        assertThat(instance)
            .isEnded
            .hasPassedInOrder(
                pathUntilContractSigned()
                    .then { it.gatewayFork }
                    .then { it.serviceTaskIssueInsurancePolicy }
                    .then { it.gatewayJoin }
                    .then { it.eventHandoverReported }
                    .then { it.eventWithdrawalPeriodElapsed }
                    .then { it.serviceTaskActivateLeasing }
                    .then { it.endEventLeasingActive },
            )
            .hasPassedInOrder(
                ProcessPath.from(FlowNodes.GatewayFork)
                    .then { it.gatewayBikeSourceJoin }
                    .then { it.serviceTaskOrderBike }
                    .then { it.gatewayBikeAvailable }
                    .then { it.gatewayJoin },
            )
            .hasNotPassed(
                FlowNodes.EndEventApplicationRejected.ELEMENT_ID,
                FlowNodes.EndEventApplicationCancelled.ELEMENT_ID,
                FlowNodes.EndEventContractCancelled.ELEMENT_ID,
            )
    }

    @Test
    fun `escalation - contract not signed in time is escalated and rejected`() {
        val businessKey = submit(age = 35, income = 3500.0)
        val instance = engine.findInstance(businessKey)

        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_VALIDATE_APPLICATION)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_SEND_CONTRACT) // -> signature wait state

        engine.fireTimer(FlowNodes.EventSignatureDeadline) // deadline -> escalation -> boundary -> rejection
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_SEND_REJECTION) // -> end

        assertThat(instance)
            .isEnded
            .hasPassedInOrder(
                pathUntilSignatureAwaited()
                    .then { it.eventSignatureDeadline }
                    .then { it.endEventNotSigned }
                    .interruptedBy(FlowNodes.SubProcessConcludeContract) { it.eventContractNotSigned }
                    .then { it.gatewayRejectionJoin }
                    .then { it.serviceTaskSendRejection }
                    .then { it.endEventApplicationRejected },
            )
            .hasNotPassed(FlowNodes.EndEventLeasingActive.ELEMENT_ID)
    }

    @Test
    fun `not solvent - the DMN routes the application straight to rejection`() {
        // age below 18 cannot sign a leasing contract, so the DMN returns solvent = false
        val businessKey = submit(age = 15, income = 3500.0)
        val instance = engine.findInstance(businessKey)

        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_VALIDATE_APPLICATION) // -> DMN -> not solvent -> rejection
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_SEND_REJECTION) // -> end

        assertThat(instance)
            .isEnded
            .hasPassedInOrder(
                pathUntilCreditRatingChecked()
                    .then { it.gatewayRejectionJoin }
                    .then { it.serviceTaskSendRejection }
                    .then { it.endEventApplicationRejected },
            )
            .hasNotPassed(
                FlowNodes.ServiceTaskSendContract.ELEMENT_ID,
                FlowNodes.EndEventLeasingActive.ELEMENT_ID,
            )
    }

    @Test
    fun `abort - withdrawing the application compensates the completed steps`() {
        // Compensation handlers run in an engine-defined order, so drive the chain generically. Only the
        // `requestCancellation` external task produces a variable the flow routes on.
        val compensationOutputs = mapOf(
            ServiceTasks.BIKE_LEASING_REQUEST_CANCELLATION to
                mapOf(CancelBikeOrderProcessApi.FlowNodes.ServiceTaskRequestCancellation.Variables.CANCELLATION_POSSIBLE.value to true),
        )

        val businessKey = submit(age = 35, income = 3500.0)
        val instance = engine.findInstance(businessKey)

        // drive to the handover wait state (contract signed, bike ordered, insured)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_VALIDATE_APPLICATION)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_SEND_CONTRACT)
        engine.correlateMessage(Messages.MIRAVELO_CONTRACT_SIGNED, businessKey)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_ISSUE_INSURANCE_POLICY)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_ORDER_BIKE, bikeAvailable)

        // withdrawing triggers compensation; it parks on the cancelBikeOrder sub-process' user task
        engine.correlateMessage(Messages.MIRAVELO_APPLICATION_WITHDRAWN, businessKey)
        engine.drainToWaitState(compensationOutputs)
        engine.completeUserTask(CancelBikeOrderProcessApi.FlowNodes.UserTaskClarifyReturn.ELEMENT_ID, mapOf("returnClarified" to true))
        engine.drainToWaitState(compensationOutputs) // -> sendCancellationConfirmation -> end cancelled

        assertThat(instance)
            .isEnded
            .hasPassed(
                ProcessPath.from(FlowNodes.StartEventApplicationWithdrawn)
                    .then { it.eventReverseApplication }
                    .throwingCompensation(FlowNodes.EventCompensateContract) { it.serviceTaskCancelContract }
                    .throwingCompensation(FlowNodes.EventCompensateInsurance) { it.serviceTaskCancelPolicy }
                    .throwingCompensation(FlowNodes.EventCompensateOrder) { it.callActivityCancelBikeOrder }
                    .then { it.serviceTaskSendCancellationConfirmation }
                    .then { it.endEventApplicationCancelled },
            )
            .hasNotPassed(FlowNodes.EndEventLeasingActive.ELEMENT_ID)
    }

    @Test
    fun `bike unavailable - clarifying an alternative re-orders and leasing becomes active`() {
        val businessKey = submit(age = 35, income = 3500.0)
        val instance = engine.findInstance(businessKey)

        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_VALIDATE_APPLICATION)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_SEND_CONTRACT)
        engine.correlateMessage(Messages.MIRAVELO_CONTRACT_SIGNED, businessKey)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_ISSUE_INSURANCE_POLICY)

        // the first order finds the requested bike unavailable -> parks on the clarify-alternative task
        val bikeUnavailable = mapOf(FlowNodes.ServiceTaskOrderBike.Variables.BIKE_AVAILABLE.value to false)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_ORDER_BIKE, bikeUnavailable)

        engine.completeUserTask(
            FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID,
            mapOf(
                FlowNodes.UserTaskClarifyAlternative.Variables.ALTERNATIVE_FOUND.value to true,
                FlowNodes.StartEventLeasingRequestReceived.Variables.BIKE_ID.value to "BIKE-ALT",
            ),
        )

        // the re-order succeeds -> parallel join -> handover wait state
        val reorderAvailable = mapOf(
            FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.value to "ORDER-2",
            FlowNodes.ServiceTaskOrderBike.Variables.BIKE_AVAILABLE.value to true,
        )
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_ORDER_BIKE, reorderAvailable)

        engine.correlateMessage(Messages.MIRAVELO_HANDOVER_REPORTED, businessKey)
        engine.fireTimer(FlowNodes.EventWithdrawalPeriodElapsed)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_ACTIVATE_LEASING)

        assertThat(instance)
            .isEnded
            .hasPassedInOrder(
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
            .hasNotPassed(
                FlowNodes.EndEventContractCancelled.ELEMENT_ID,
                FlowNodes.EndEventApplicationRejected.ELEMENT_ID,
            )
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

    /** Starts the process through its message start event, keyed by a fresh application business key. */
    private fun submit(age: Int, income: Double, bikeId: String = "BIKE-TEST"): String {
        val businessKey = UUID.randomUUID().toString()
        engine.startLeasing(businessKey, age = age, income = income, bikeId = bikeId)
        return businessKey
    }
}
