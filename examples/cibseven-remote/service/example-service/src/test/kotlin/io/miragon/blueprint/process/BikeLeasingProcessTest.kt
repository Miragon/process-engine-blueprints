package io.miragon.blueprint.process

import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.Elements
import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.Messages
import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.ServiceTasks
import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.Variables
import org.cibseven.bpm.engine.ProcessEngine
import org.cibseven.bpm.engine.delegate.ExecutionListener
import org.cibseven.bpm.engine.delegate.TaskListener
import org.cibseven.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration
import org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat
import org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.init
import org.cibseven.bpm.engine.test.mock.MockExpressionManager
import org.cibseven.bpm.engine.test.mock.Mocks
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
        Variables.ServiceTaskOrderBike.ORDER_ID.value to "ORDER-1",
        Variables.ServiceTaskOrderBike.BIKE_AVAILABLE.value to true,
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
        engine.fireTimer(Elements.EVENT_WITHDRAWAL_PERIOD_ELAPSED)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_ACTIVATE_LEASING) // flips read model to ACTIVE -> end

        assertThat(instance)
            .isEnded
            .hasPassedInOrder(
                Elements.SERVICE_TASK_VALIDATE_APPLICATION.value,
                Elements.BUSINESS_RULE_TASK_CHECK_CREDIT_RATING.value,
                Elements.SERVICE_TASK_SEND_CONTRACT.value,
                Elements.SERVICE_TASK_ISSUE_INSURANCE_POLICY.value,
                Elements.EVENT_HANDOVER_REPORTED.value,
                Elements.SERVICE_TASK_ACTIVATE_LEASING.value,
                Elements.END_EVENT_LEASING_ACTIVE.value,
            )
            .hasNotPassed(
                Elements.END_EVENT_APPLICATION_REJECTED.value,
                Elements.END_EVENT_APPLICATION_CANCELLED.value,
                Elements.END_EVENT_CONTRACT_CANCELLED.value,
            )
    }

    @Test
    fun `escalation - contract not signed in time is escalated and rejected`() {
        val businessKey = submit(age = 35, income = 3500.0)
        val instance = engine.findInstance(businessKey)

        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_VALIDATE_APPLICATION)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_SEND_CONTRACT) // -> signature wait state

        engine.fireTimer(Elements.EVENT_SIGNATURE_DEADLINE) // deadline -> escalation -> boundary -> rejection
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_SEND_REJECTION) // -> end

        assertThat(instance)
            .isEnded
            .hasPassed(
                Elements.EVENT_SIGNATURE_DEADLINE.value,
                Elements.EVENT_CONTRACT_NOT_SIGNED.value,
                Elements.SERVICE_TASK_SEND_REJECTION.value,
                Elements.END_EVENT_APPLICATION_REJECTED.value,
            )
            .hasNotPassed(Elements.END_EVENT_LEASING_ACTIVE.value)
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
            .hasPassed(
                Elements.SERVICE_TASK_VALIDATE_APPLICATION.value,
                Elements.BUSINESS_RULE_TASK_CHECK_CREDIT_RATING.value,
                Elements.SERVICE_TASK_SEND_REJECTION.value,
                Elements.END_EVENT_APPLICATION_REJECTED.value,
            )
            .hasNotPassed(
                Elements.SERVICE_TASK_SEND_CONTRACT.value,
                Elements.END_EVENT_LEASING_ACTIVE.value,
            )
    }

    @Test
    fun `abort - withdrawing the application compensates the completed steps`() {
        // Compensation handlers run in an engine-defined order, so drive the chain generically. Only the
        // `requestCancellation` external task produces a variable the flow routes on.
        val compensationOutputs = mapOf(
            CancelBikeOrderProcessApi.ServiceTasks.BIKE_LEASING_REQUEST_CANCELLATION to
                mapOf(CancelBikeOrderProcessApi.Variables.ServiceTaskRequestCancellation.CANCELLATION_POSSIBLE.value to true),
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
        engine.completeUserTask(CancelBikeOrderProcessApi.Elements.USER_TASK_CLARIFY_RETURN.value, mapOf("returnClarified" to true))
        engine.drainToWaitState(compensationOutputs) // -> sendCancellationConfirmation -> end cancelled

        assertThat(instance)
            .isEnded
            .hasPassed(
                Elements.SERVICE_TASK_CANCEL_CONTRACT.value,
                Elements.SERVICE_TASK_CANCEL_POLICY.value,
                Elements.CALL_ACTIVITY_CANCEL_BIKE_ORDER.value,
                Elements.SERVICE_TASK_SEND_CANCELLATION_CONFIRMATION.value,
                Elements.END_EVENT_APPLICATION_CANCELLED.value,
            )
            .hasNotPassed(Elements.END_EVENT_LEASING_ACTIVE.value)
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
        val bikeUnavailable = mapOf(Variables.ServiceTaskOrderBike.BIKE_AVAILABLE.value to false)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_ORDER_BIKE, bikeUnavailable)

        engine.completeUserTask(
            Elements.USER_TASK_CLARIFY_ALTERNATIVE.value,
            mapOf(
                Variables.UserTaskClarifyAlternative.ALTERNATIVE_FOUND.value to true,
                Variables.StartEventLeasingRequestReceived.BIKE_ID.value to "BIKE-ALT",
            ),
        )

        // the re-order succeeds -> parallel join -> handover wait state
        val reorderAvailable = mapOf(
            Variables.ServiceTaskOrderBike.ORDER_ID.value to "ORDER-2",
            Variables.ServiceTaskOrderBike.BIKE_AVAILABLE.value to true,
        )
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_ORDER_BIKE, reorderAvailable)

        engine.correlateMessage(Messages.MIRAVELO_HANDOVER_REPORTED, businessKey)
        engine.fireTimer(Elements.EVENT_WITHDRAWAL_PERIOD_ELAPSED)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_ACTIVATE_LEASING)

        assertThat(instance)
            .isEnded
            .hasPassed(
                Elements.USER_TASK_CLARIFY_ALTERNATIVE.value,
                Elements.SERVICE_TASK_ORDER_BIKE.value,
                Elements.SERVICE_TASK_ACTIVATE_LEASING.value,
                Elements.END_EVENT_LEASING_ACTIVE.value,
            )
            .hasNotPassed(
                Elements.END_EVENT_CONTRACT_CANCELLED.value,
                Elements.END_EVENT_APPLICATION_REJECTED.value,
            )
    }

    /** Starts the process through its message start event, keyed by a fresh application business key. */
    private fun submit(age: Int, income: Double, bikeId: String = "BIKE-TEST"): String {
        val businessKey = UUID.randomUUID().toString()
        engine.startLeasing(businessKey, age = age, income = income, bikeId = bikeId)
        return businessKey
    }
}
