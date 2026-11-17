package io.miragon.blueprint.adapter.process

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes
import io.miragon.bpmn.runtime.path.ProcessPath
import io.miragon.bpmn.runtime.path.enter
import io.miragon.bpmn.runtime.path.inside
import io.miragon.bpmn.runtime.path.interruptedBy
import io.miragon.bpmn.runtime.path.onto
import io.miragon.bpmn.runtime.path.then
import io.miragon.bpmn.runtime.path.throwingCompensation
import org.assertj.core.api.Assertions
import org.operaton.bpm.engine.ProcessEngine
import org.operaton.bpm.engine.delegate.ExecutionListener
import org.operaton.bpm.engine.delegate.TaskListener
import org.operaton.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration
import org.operaton.bpm.engine.runtime.ProcessInstance
import org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat
import org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.calledProcessInstance
import org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.execute
import org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.init
import org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.job
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
 * messages and timers are released explicitly, and each async continuation runs with its own
 * `execute(job(...))`, one job per call. It therefore verifies the *topology* — routing, gateways,
 * compensation, the event sub-process, the DMN and the timers — independent of what the workers
 * actually do with each task.
 */
class BikeLeasingProcessTest {

    private lateinit var engine: ProcessEngine

    /** Output of the `orderBike` external task once the dealer accepted the order. */
    private val orderPlaced = mapOf(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.value to "ORDER-1")

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
        // given
        val businessKey = submit(age = 35, income = 3500.0)
        val instance = engine.findInstance(businessKey)

        // when
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance))
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance))
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_SEND_CONTRACT)
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance)) // -> signature wait state
        engine.correlateMessage(Messages.MIRAVELO_CONTRACT_SIGNED, businessKey)
        execute(job(FlowNodes.EventContractSigned.ELEMENT_ID, instance)) // forks into insurance + bike order
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_ISSUE_INSURANCE_POLICY)
        execute(job(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, instance))
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance))
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_ORDER_BIKE, orderPlaced)
        execute(job(FlowNodes.ServiceTaskOrderBike.ELEMENT_ID, instance))
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance)) // -> handover wait state
        engine.correlateMessage(Messages.MIRAVELO_HANDOVER_REPORTED, businessKey)
        execute(job(FlowNodes.EventHandoverReported.ELEMENT_ID, instance)) // -> withdrawal-period timer
        engine.fireTimer(FlowNodes.EventWithdrawalPeriodElapsed)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_ACTIVATE_LEASING)
        execute(job(FlowNodes.ServiceTaskActivateLeasing.ELEMENT_ID, instance)) // -> end

        // then
        assertThat(instance)
            .isEnded
            .hasPassedInOrder(
                ProcessPath.from(FlowNodes.StartEventLeasingRequestReceived)
                    .then { it.businessRuleTaskCheckCreditRating }
                    .then { it.gatewayIsSolvent }
                    .onto { it.subProcessConcludeContract }
                    .inside {
                        enter { it.startEventCustomerEligible }
                            .then { it.serviceTaskSendContract }
                            .then { it.gatewayAwaitSignature }
                            .then { it.eventContractSigned }
                            .then { it.endEventContractValid }
                    }
                    .then { it.gatewayFork }
                    .then { it.serviceTaskIssueInsurancePolicy }
                    .then { it.gatewayJoin }
                    .then { it.eventHandoverReported }
                    .then { it.eventWithdrawalPeriodElapsed }
                    .then { it.serviceTaskActivateLeasing }
                    .then { it.endEventLeasingActive },
            )
            .hasPassedInOrder(
                ProcessPath.from(FlowNodes.StartEventLeasingRequestReceived)
                    .then { it.businessRuleTaskCheckCreditRating }
                    .then { it.gatewayIsSolvent }
                    .onto { it.subProcessConcludeContract }
                    .inside {
                        enter { it.startEventCustomerEligible }
                            .then { it.serviceTaskSendContract }
                            .then { it.gatewayAwaitSignature }
                            .then { it.eventContractSigned }
                            .then { it.endEventContractValid }
                    }
                    .then { it.gatewayFork }
                    .then { it.gatewayBikeSourceJoin }
                    .then { it.serviceTaskOrderBike }
                    .then { it.gatewayJoin },
            )
            .hasNotPassed(
                FlowNodes.EventBikeUnavailable.ELEMENT_ID,
                FlowNodes.EndEventApplicationRejected.ELEMENT_ID,
                FlowNodes.EndEventApplicationCancelled.ELEMENT_ID,
                FlowNodes.EndEventContractCancelled.ELEMENT_ID,
            )
    }

    @Test
    fun `escalation - contract not signed in time is escalated and rejected`() {
        // given
        val businessKey = submit(age = 35, income = 3500.0)
        val instance = engine.findInstance(businessKey)

        // when
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance))
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance))
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_SEND_CONTRACT)
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance)) // -> signature wait state
        engine.fireTimer(FlowNodes.EventSignatureDeadline) // deadline -> escalation -> boundary -> rejection
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_SEND_REJECTION)
        execute(job(FlowNodes.ServiceTaskSendRejection.ELEMENT_ID, instance)) // -> end

        // then
        assertThat(instance)
            .isEnded
            .hasPassedInOrder(
                ProcessPath.from(FlowNodes.StartEventLeasingRequestReceived)
                    .then { it.businessRuleTaskCheckCreditRating }
                    .then { it.gatewayIsSolvent }
                    .onto { it.subProcessConcludeContract }
                    .enter { it.startEventCustomerEligible }
                    .then { it.serviceTaskSendContract }
                    .then { it.gatewayAwaitSignature }
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
        // given
        // age below 18 cannot sign a leasing contract, so the DMN returns solvent = false
        val businessKey = submit(age = 15, income = 3500.0)
        val instance = engine.findInstance(businessKey)

        // when
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance))
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance)) // the DMN (not solvent) parks on the rejection
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_SEND_REJECTION)
        execute(job(FlowNodes.ServiceTaskSendRejection.ELEMENT_ID, instance)) // -> end

        // then
        assertThat(instance)
            .isEnded
            .hasPassedInOrder(
                ProcessPath.from(FlowNodes.StartEventLeasingRequestReceived)
                    .then { it.businessRuleTaskCheckCreditRating }
                    .then { it.gatewayIsSolvent }
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
    fun `reminder - an unsigned contract gets one reminder before the deadline rejects it`() {
        // given
        val businessKey = submit(age = 35, income = 3500.0)
        val instance = engine.findInstance(businessKey)

        // when
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance))
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance))
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_SEND_CONTRACT)
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance)) // -> signature wait state
        engine.fireTimer(FlowNodes.EventSignatureReminder) // non-interrupting -> reminder mail
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_SEND_REMINDER_MAIL)
        execute(job(FlowNodes.ServiceTaskSendReminderMail.ELEMENT_ID, instance))
        engine.fireTimer(FlowNodes.EventSignatureDeadline) // deadline -> escalation -> boundary -> rejection
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_SEND_REJECTION)
        execute(job(FlowNodes.ServiceTaskSendRejection.ELEMENT_ID, instance)) // -> end

        // then
        assertThat(instance)
            .isEnded
            .hasPassedInOrder(
                ProcessPath.from(FlowNodes.StartEventLeasingRequestReceived)
                    .then { it.businessRuleTaskCheckCreditRating }
                    .then { it.gatewayIsSolvent }
                    .onto { it.subProcessConcludeContract }
                    .interruptedBy(FlowNodes.SubProcessConcludeContract) { it.eventSignatureReminder }
                    .then { it.serviceTaskSendReminderMail }
                    .then { it.endEventProspectReminded },
            )
            .hasPassed(FlowNodes.EndEventApplicationRejected.ELEMENT_ID)
    }

    @Test
    fun `abort - withdrawing the application compensates the completed steps`() {
        // given
        val businessKey = submit(age = 35, income = 3500.0)
        val instance = engine.findInstance(businessKey)

        // when
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance))
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance))
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_SEND_CONTRACT)
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance)) // -> signature wait state
        engine.correlateMessage(Messages.MIRAVELO_CONTRACT_SIGNED, businessKey)
        execute(job(FlowNodes.EventContractSigned.ELEMENT_ID, instance)) // forks into insurance + bike order
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_ISSUE_INSURANCE_POLICY)
        execute(job(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, instance))
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance))
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_ORDER_BIKE, orderPlaced)
        execute(job(FlowNodes.ServiceTaskOrderBike.ELEMENT_ID, instance))
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance)) // -> handover wait state
        engine.correlateMessage(Messages.MIRAVELO_APPLICATION_WITHDRAWN, businessKey)
        execute(job(FlowNodes.StartEventApplicationWithdrawn.ELEMENT_ID, instance)) // -> compensation
        execute(job(CancelBikeOrderProcessApi.FlowNodes.StartEventCancellationRequired.ELEMENT_ID, calledProcessInstance(instance)))
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_CANCEL_POLICY)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_CANCEL_CONTRACT)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_REQUEST_CANCELLATION, mapOf(CancelBikeOrderProcessApi.FlowNodes.ServiceTaskRequestCancellation.Variables.CANCELLATION_POSSIBLE.value to false)) // the dealer refuses to cancel the order
        execute(job(CancelBikeOrderProcessApi.FlowNodes.ServiceTaskRequestCancellation.ELEMENT_ID, calledProcessInstance(instance))) // -> parks on the clarify-return task
        engine.completeUserTask(CancelBikeOrderProcessApi.FlowNodes.UserTaskClarifyReturn.ELEMENT_ID)
        execute(job(CancelBikeOrderProcessApi.FlowNodes.UserTaskClarifyReturn.ELEMENT_ID, calledProcessInstance(instance)))
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_BOOK_COSTS)
        execute(job(CancelBikeOrderProcessApi.FlowNodes.ServiceTaskBookCosts.ELEMENT_ID, calledProcessInstance(instance)))
        execute(job(CancelBikeOrderProcessApi.FlowNodes.GatewayJoin.ELEMENT_ID, calledProcessInstance(instance)))
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_SEND_CANCELLATION_CONFIRMATION)
        execute(job(FlowNodes.ServiceTaskSendCancellationConfirmation.ELEMENT_ID, instance)) // -> end cancelled

        // then
        assertThat(instance)
            .isEnded
            .hasPassed(
                ProcessPath.from(FlowNodes.StartEventLeasingRequestReceived)
                    .then { it.businessRuleTaskCheckCreditRating }
                    .then { it.gatewayIsSolvent }
                    .onto { it.subProcessConcludeContract }
                    .inside {
                        enter { it.startEventCustomerEligible }
                            .then { it.serviceTaskSendContract }
                            .then { it.gatewayAwaitSignature }
                            .then { it.eventContractSigned }
                            .then { it.endEventContractValid }
                    }
                    .then { it.gatewayFork }
                    .then { it.gatewayBikeSourceJoin }
                    .then { it.serviceTaskOrderBike }
                    .then { it.gatewayJoin }
                    .enter(FlowNodes.SubProcessApplicationWithdrawn) { it.startEventApplicationWithdrawn }
                    .then { it.eventReverseApplication }
                    .throwingCompensation(FlowNodes.EventCompensateContract) { it.serviceTaskCancelContract }
                    .throwingCompensation(FlowNodes.EventCompensateInsurance) { it.serviceTaskCancelPolicy }
                    .throwingCompensation(FlowNodes.EventCompensateOrder) { it.callActivityCancelBikeOrder }
                    .then { it.serviceTaskSendCancellationConfirmation }
                    .then { it.endEventApplicationCancelled },
            )
            .hasNotPassed(FlowNodes.EndEventLeasingActive.ELEMENT_ID)
        Assertions.assertThat(engine.cancellationCostBookings(instance)).isEqualTo(1)
    }

    @Test
    fun `bike unavailable - clarifying an alternative re-orders and leasing becomes active`() {
        // given
        val businessKey = submit(age = 35, income = 3500.0)
        val instance = engine.findInstance(businessKey)

        // when
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance))
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance))
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_SEND_CONTRACT)
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance)) // -> signature wait state
        engine.correlateMessage(Messages.MIRAVELO_CONTRACT_SIGNED, businessKey)
        execute(job(FlowNodes.EventContractSigned.ELEMENT_ID, instance)) // forks into insurance + bike order
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_ISSUE_INSURANCE_POLICY)
        execute(job(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, instance))
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance))
        engine.raiseBpmnErrorOnExternalTask(ServiceTasks.BIKE_LEASING_ORDER_BIKE, Errors.BIKE_UNAVAILABLE) // -> parks on the clarify-alternative task
        engine.acceptAlternative()
        execute(job(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID, instance))
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_ORDER_BIKE, orderPlaced) // the re-order succeeds
        execute(job(FlowNodes.ServiceTaskOrderBike.ELEMENT_ID, instance))
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance)) // -> handover wait state
        engine.correlateMessage(Messages.MIRAVELO_HANDOVER_REPORTED, businessKey)
        execute(job(FlowNodes.EventHandoverReported.ELEMENT_ID, instance)) // -> withdrawal-period timer
        engine.fireTimer(FlowNodes.EventWithdrawalPeriodElapsed)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_ACTIVATE_LEASING)
        execute(job(FlowNodes.ServiceTaskActivateLeasing.ELEMENT_ID, instance)) // -> end

        // then
        assertThat(instance)
            .isEnded
            .hasPassedInOrder(
                ProcessPath.from(FlowNodes.StartEventLeasingRequestReceived)
                    .then { it.businessRuleTaskCheckCreditRating }
                    .then { it.gatewayIsSolvent }
                    .onto { it.subProcessConcludeContract }
                    .inside {
                        enter { it.startEventCustomerEligible }
                            .then { it.serviceTaskSendContract }
                            .then { it.gatewayAwaitSignature }
                            .then { it.eventContractSigned }
                            .then { it.endEventContractValid }
                    }
                    .then { it.gatewayFork }
                    .then { it.gatewayBikeSourceJoin }
                    .then { it.serviceTaskOrderBike }
                    .interruptedBy(FlowNodes.ServiceTaskOrderBike) { it.eventBikeUnavailable }
                    .then { it.userTaskClarifyAlternative }
                    .then { it.gatewayAlternativeFound }
                    .then { it.gatewayBikeSourceJoin }
                    .then { it.serviceTaskOrderBike }
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
        Assertions.assertThat(engine.bikeOrderedBy(instance)).isEqualTo("BIKE-ALT")
    }

    @Test
    fun `bike unavailable - declining the alternative reverses contract and policy without cancelling an order`() {
        // given
        val businessKey = submitUntilBikeUnavailable()
        val instance = engine.findInstance(businessKey)

        // when
        engine.completeUserTask(
            FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID,
            mapOf(FlowNodes.UserTaskClarifyAlternative.Variables.ALTERNATIVE_FOUND.value to false),
        )
        execute(job(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID, instance)) // -> compensation
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_CANCEL_POLICY)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_CANCEL_CONTRACT)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_SEND_CANCELLATION_CONFIRMATION)
        execute(job(FlowNodes.ServiceTaskConfirmContractCancellation.ELEMENT_ID, instance)) // -> end cancelled

        // then
        assertThat(instance)
            .isEnded
            .hasPassed(
                ProcessPath.from(FlowNodes.StartEventLeasingRequestReceived)
                    .then { it.businessRuleTaskCheckCreditRating }
                    .then { it.gatewayIsSolvent }
                    .onto { it.subProcessConcludeContract }
                    .inside {
                        enter { it.startEventCustomerEligible }
                            .then { it.serviceTaskSendContract }
                            .then { it.gatewayAwaitSignature }
                            .then { it.eventContractSigned }
                            .then { it.endEventContractValid }
                    }
                    .then { it.gatewayFork }
                    .then { it.gatewayBikeSourceJoin }
                    .then { it.serviceTaskOrderBike }
                    .interruptedBy(FlowNodes.ServiceTaskOrderBike) { it.eventBikeUnavailable }
                    .then { it.userTaskClarifyAlternative }
                    .then { it.gatewayAlternativeFound }
                    .then { it.eventTriggerReversal }
                    .throwingCompensation(FlowNodes.EventCompensateContract) { it.serviceTaskCancelContract }
                    .throwingCompensation(FlowNodes.EventCompensateInsurance) { it.serviceTaskCancelPolicy }
                    .then { it.serviceTaskConfirmContractCancellation }
                    .then { it.endEventContractCancelled },
            )
            .hasNotPassed(
                FlowNodes.CallActivityCancelBikeOrder.ELEMENT_ID,
                FlowNodes.EndEventLeasingActive.ELEMENT_ID,
            )
    }

    @Test
    fun `abort while clarifying an alternative - compensates contract and policy without cancelling an order`() {
        // given
        val businessKey = submitUntilBikeUnavailable()
        val instance = engine.findInstance(businessKey)

        // when
        engine.correlateMessage(Messages.MIRAVELO_APPLICATION_WITHDRAWN, businessKey)
        execute(job(FlowNodes.StartEventApplicationWithdrawn.ELEMENT_ID, instance)) // -> compensation
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_CANCEL_POLICY)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_CANCEL_CONTRACT)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_SEND_CANCELLATION_CONFIRMATION)
        execute(job(FlowNodes.ServiceTaskSendCancellationConfirmation.ELEMENT_ID, instance)) // -> end cancelled

        // then
        assertThat(instance)
            .isEnded
            .hasPassed(
                ProcessPath.from(FlowNodes.StartEventLeasingRequestReceived)
                    .then { it.businessRuleTaskCheckCreditRating }
                    .then { it.gatewayIsSolvent }
                    .onto { it.subProcessConcludeContract }
                    .inside {
                        enter { it.startEventCustomerEligible }
                            .then { it.serviceTaskSendContract }
                            .then { it.gatewayAwaitSignature }
                            .then { it.eventContractSigned }
                            .then { it.endEventContractValid }
                    }
                    .then { it.gatewayFork }
                    .then { it.gatewayBikeSourceJoin }
                    .then { it.serviceTaskOrderBike }
                    .interruptedBy(FlowNodes.ServiceTaskOrderBike) { it.eventBikeUnavailable }
                    .then { it.userTaskClarifyAlternative }
                    .enter(FlowNodes.SubProcessApplicationWithdrawn) { it.startEventApplicationWithdrawn }
                    .then { it.eventReverseApplication }
                    .throwingCompensation(FlowNodes.EventCompensateContract) { it.serviceTaskCancelContract }
                    .throwingCompensation(FlowNodes.EventCompensateInsurance) { it.serviceTaskCancelPolicy }
                    .then { it.serviceTaskSendCancellationConfirmation }
                    .then { it.endEventApplicationCancelled },
            )
            .hasNotPassed(FlowNodes.CallActivityCancelBikeOrder.ELEMENT_ID)
    }

    @Test
    fun `abort after an accepted alternative - cancels the one placed order exactly once`() {
        // given
        val businessKey = submitUntilBikeUnavailable()
        val instance = engine.findInstance(businessKey)

        // when
        engine.acceptAlternative()
        execute(job(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID, instance))
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_ORDER_BIKE, orderPlaced) // the re-order succeeds
        execute(job(FlowNodes.ServiceTaskOrderBike.ELEMENT_ID, instance))
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance)) // -> handover wait state
        engine.correlateMessage(Messages.MIRAVELO_APPLICATION_WITHDRAWN, businessKey)
        execute(job(FlowNodes.StartEventApplicationWithdrawn.ELEMENT_ID, instance)) // -> compensation
        execute(job(CancelBikeOrderProcessApi.FlowNodes.StartEventCancellationRequired.ELEMENT_ID, calledProcessInstance(instance)))
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_CANCEL_POLICY)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_CANCEL_CONTRACT)
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_REQUEST_CANCELLATION, mapOf(CancelBikeOrderProcessApi.FlowNodes.ServiceTaskRequestCancellation.Variables.CANCELLATION_POSSIBLE.value to true)) // the dealer cancels the order
        execute(job(CancelBikeOrderProcessApi.FlowNodes.ServiceTaskRequestCancellation.ELEMENT_ID, calledProcessInstance(instance)))
        execute(job(CancelBikeOrderProcessApi.FlowNodes.GatewayJoin.ELEMENT_ID, calledProcessInstance(instance))) // -> order reversed without a return to clarify
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_SEND_CANCELLATION_CONFIRMATION)
        execute(job(FlowNodes.ServiceTaskSendCancellationConfirmation.ELEMENT_ID, instance)) // -> end cancelled

        // then
        assertThat(instance).isEnded.hasPassed(FlowNodes.EndEventApplicationCancelled.ELEMENT_ID)
        val orderCancellations = engine.historyService.createHistoricProcessInstanceQuery()
            .processDefinitionKey(CancelBikeOrderProcessApi.PROCESS_ID.value)
            .count()
        Assertions.assertThat(orderCancellations).isEqualTo(1)
        Assertions.assertThat(engine.cancellationCostBookings(instance)).isZero()
    }

    /** Drives a signed, insured application to the clarify-alternative task: the dealer has no bike. */
    private fun submitUntilBikeUnavailable(): String {
        val businessKey = submit(age = 35, income = 3500.0)
        val instance = engine.findInstance(businessKey)
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance))
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance))
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_SEND_CONTRACT)
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance)) // -> signature wait state
        engine.correlateMessage(Messages.MIRAVELO_CONTRACT_SIGNED, businessKey)
        execute(job(FlowNodes.EventContractSigned.ELEMENT_ID, instance)) // forks into insurance + bike order
        engine.completeExternalTask(ServiceTasks.BIKE_LEASING_ISSUE_INSURANCE_POLICY)
        execute(job(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, instance))
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance))
        engine.raiseBpmnErrorOnExternalTask(ServiceTasks.BIKE_LEASING_ORDER_BIKE, Errors.BIKE_UNAVAILABLE) // -> parks on the clarify-alternative task
        return businessKey
    }

    private fun ProcessEngine.acceptAlternative() =
        completeUserTask(
            FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID,
            mapOf(
                FlowNodes.UserTaskClarifyAlternative.Variables.ALTERNATIVE_FOUND.value to true,
                FlowNodes.UserTaskClarifyAlternative.Variables.BIKE_ID.value to "BIKE-ALT",
            ),
        )

    /** How often the order cancellations called by [instance] booked the dealer's cancellation costs. */
    private fun ProcessEngine.cancellationCostBookings(instance: ProcessInstance): Long =
        historyService.createHistoricProcessInstanceQuery()
            .superProcessInstanceId(instance.id)
            .list()
            .sumOf { orderCancellation ->
                historyService.createHistoricActivityInstanceQuery()
                    .processInstanceId(orderCancellation.id)
                    .activityId(CancelBikeOrderProcessApi.FlowNodes.ServiceTaskBookCosts.ELEMENT_ID)
                    .count()
            }

    /** The bike [instance] carried when it ended — the one its last `orderBike` external task handed to the worker. */
    private fun ProcessEngine.bikeOrderedBy(instance: ProcessInstance): Any? =
        historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(instance.id)
            .variableName(FlowNodes.ServiceTaskOrderBike.Variables.BIKE_ID.value)
            .singleResult()!!
            .value

    /** Starts the process through its message start event, keyed by a fresh application business key. */
    private fun submit(age: Int, income: Double, bikeId: String = "BIKE-TEST"): String {
        val businessKey = UUID.randomUUID().toString()
        engine.startLeasing(businessKey, age = age, income = income, bikeId = bikeId)
        return businessKey
    }
}
