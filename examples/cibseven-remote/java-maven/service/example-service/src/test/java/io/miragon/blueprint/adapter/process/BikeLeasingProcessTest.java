package io.miragon.blueprint.adapter.process;

import static io.miragon.blueprint.adapter.process.ProcessDriveUtils.completeExternalTask;
import static io.miragon.blueprint.adapter.process.ProcessDriveUtils.completeUserTask;
import static io.miragon.blueprint.adapter.process.ProcessDriveUtils.correlateMessage;
import static io.miragon.blueprint.adapter.process.ProcessDriveUtils.findInstance;
import static io.miragon.blueprint.adapter.process.ProcessDriveUtils.fireTimer;
import static io.miragon.blueprint.adapter.process.ProcessDriveUtils.raiseBpmnErrorOnExternalTask;
import static io.miragon.blueprint.adapter.process.ProcessDriveUtils.startLeasing;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.calledProcessInstance;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.execute;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.init;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.job;

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes;
import io.miragon.bpmn.runtime.path.PathWalk;
import org.assertj.core.api.Assertions;
import org.cibseven.bpm.engine.ProcessEngine;
import org.cibseven.bpm.engine.delegate.ExecutionListener;
import org.cibseven.bpm.engine.delegate.TaskListener;
import org.cibseven.bpm.engine.runtime.ProcessInstance;
import org.cibseven.bpm.engine.test.Deployment;
import org.cibseven.bpm.engine.test.mock.MockExpressionManager;
import org.cibseven.bpm.engine.test.mock.Mocks;
import org.cibseven.community.process_test_coverage.engine.platform7.ProcessCoverageInMemProcessEngineConfiguration;
import org.cibseven.community.process_test_coverage.junit5.platform7.ProcessEngineCoverageExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;
import java.util.UUID;

/**
 * Process-model behaviour test for the {@code bike-leasing} model — it lives with the <b>process owner</b>
 * (this service), which also owns and deploys the model. Because the worker embeds no engine, the test
 * runs a <b>standalone in-memory engine</b> that records process test coverage, and deploys the model
 * from this service's own resources for each test.
 *
 * <p>The service tasks are <b>external tasks</b>, so the test completes each one explicitly by topic via
 * {@link ProcessDriveUtils#completeExternalTask(ProcessEngine, String, Map)} (supplying the output variables
 * a real worker would return); user tasks, messages and timers are released explicitly, and each async
 * continuation runs with its own {@code execute(job(...))}, one job per call. It therefore verifies the
 * <i>topology</i> — routing, gateways, compensation, the event sub-process, the DMN and the timers —
 * independent of what the workers actually do with each task.
 */
@Deployment(resources = {"bpmn/bike-leasing.bpmn", "bpmn/cancel-bike-order.bpmn", "dmn/check-credit-rating.dmn"})
class BikeLeasingProcessTest {

    private ProcessEngine engine;

    /** Output of the {@code orderBike} external task once the dealer accepted the order. */
    private final Map<String, Object> orderPlaced =
            Map.of(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.getValue(), "ORDER-1");

    @RegisterExtension
    static final ProcessEngineCoverageExtension COVERAGE = ProcessEngineCoverageExtension.builder(coverageEngineConfiguration()).build();

    private static ProcessCoverageInMemProcessEngineConfiguration coverageEngineConfiguration() {
        ProcessCoverageInMemProcessEngineConfiguration configuration = new ProcessCoverageInMemProcessEngineConfiguration();
        configuration.setJdbcUrl("jdbc:h2:mem:bikeleasing-process;DB_CLOSE_DELAY=1000");
        configuration.setExpressionManager(new MockExpressionManager());
        return configuration;
    }

    @BeforeEach
    void setUp() {
        // The job executor stays off by default, so the test drives async continuations by hand.
        // The model references two listeners by expression (`#{bikeOrderAuditListener}`,
        // `#{clarifyAlternativeTaskListener}`) whose real beans live in the `engine-service` — listeners
        // run inside the engine and have no external-task equivalent, so they cannot be worker beans.
        // The standalone test engine has no Spring context, so a `MockExpressionManager` + `Mocks`
        // registers no-op stand-ins under those bean names, letting the topology test resolve (and thus
        // verify) the `delegateExpression`s without pulling in the engine module.
        engine = COVERAGE.getProcessEngine();

        Mocks.register("bikeOrderAuditListener", (ExecutionListener) execution -> {
            /* stand-in: real bean in engine-service */
        });
        Mocks.register("clarifyAlternativeTaskListener", (TaskListener) delegateTask -> {
            /* stand-in: real bean in engine-service */
        });

        init(engine);
    }

    @AfterEach
    void tearDown() {
        Mocks.reset();
    }

    @Test
    @DisplayName("happy path - contract signed, bike available, leasing becomes active")
    void happyPathContractSignedBikeAvailableLeasingBecomesActive() {
        // given
        String businessKey = submit(35, 3500.0);
        ProcessInstance instance = findInstance(engine, businessKey);

        // when
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_SEND_CONTRACT);
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance)); // -> signature wait state
        correlateMessage(engine, Messages.MIRAVELO_CONTRACT_SIGNED, businessKey);
        execute(job(FlowNodes.EventContractSigned.ELEMENT_ID, instance)); // forks into insurance + bike order
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_ISSUE_INSURANCE_POLICY);
        execute(job(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, instance));
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance));
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_ORDER_BIKE, orderPlaced);
        execute(job(FlowNodes.ServiceTaskOrderBike.ELEMENT_ID, instance));
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance)); // -> handover wait state
        correlateMessage(engine, Messages.MIRAVELO_HANDOVER_REPORTED, businessKey);
        execute(job(FlowNodes.EventHandoverReported.ELEMENT_ID, instance)); // -> withdrawal-period timer
        fireTimer(engine, FlowNodes.EventWithdrawalPeriodElapsed.INSTANCE);
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_ACTIVATE_LEASING);
        execute(job(FlowNodes.ServiceTaskActivateLeasing.ELEMENT_ID, instance)); // -> end

        // then
        assertThat(instance)
                .isEnded()
                .hasPassedInOrder(PathWalk.from(FlowNodes.StartEventLeasingRequestReceived.INSTANCE)
                        .then(next -> next.businessRuleTaskCheckCreditRating())
                        .then(next -> next.gatewayIsSolvent())
                        .onto(next -> next.subProcessConcludeContract())
                        .inside(FlowNodes.SubProcessConcludeContract.INSTANCE, start ->
                                PathWalk.from(start.startEventCustomerEligible())
                                        .then(next -> next.serviceTaskSendContract())
                                        .then(next -> next.gatewayAwaitSignature())
                                        .then(next -> next.eventContractSigned())
                                        .end(next -> next.endEventContractValid()))
                        .then(next -> next.gatewayFork())
                        .then(next -> next.serviceTaskIssueInsurancePolicy())
                        .then(next -> next.gatewayJoin())
                        .then(next -> next.eventHandoverReported())
                        .then(next -> next.eventWithdrawalPeriodElapsed())
                        .then(next -> next.serviceTaskActivateLeasing())
                        .end(next -> next.endEventLeasingActive())
                        .getIds())
                .hasPassedInOrder(PathWalk.from(FlowNodes.StartEventLeasingRequestReceived.INSTANCE)
                        .then(next -> next.businessRuleTaskCheckCreditRating())
                        .then(next -> next.gatewayIsSolvent())
                        .onto(next -> next.subProcessConcludeContract())
                        .inside(FlowNodes.SubProcessConcludeContract.INSTANCE, start ->
                                PathWalk.from(start.startEventCustomerEligible())
                                        .then(next -> next.serviceTaskSendContract())
                                        .then(next -> next.gatewayAwaitSignature())
                                        .then(next -> next.eventContractSigned())
                                        .end(next -> next.endEventContractValid()))
                        .then(next -> next.gatewayFork())
                        .then(next -> next.gatewayBikeSourceJoin())
                        .then(next -> next.serviceTaskOrderBike())
                        .then(next -> next.gatewayJoin())
                        .getIds())
                .hasNotPassed(
                        FlowNodes.EventBikeUnavailable.ELEMENT_ID,
                        FlowNodes.EndEventApplicationRejected.ELEMENT_ID,
                        FlowNodes.EndEventApplicationCancelled.ELEMENT_ID,
                        FlowNodes.EndEventContractCancelled.ELEMENT_ID);
    }

    @Test
    @DisplayName("escalation - contract not signed in time is escalated and rejected")
    void escalationContractNotSignedInTimeIsEscalatedAndRejected() {
        // given
        String businessKey = submit(35, 3500.0);
        ProcessInstance instance = findInstance(engine, businessKey);

        // when
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_SEND_CONTRACT);
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance)); // -> signature wait state
        fireTimer(engine, FlowNodes.EventSignatureDeadline.INSTANCE); // deadline -> escalation -> boundary -> rejection
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_SEND_REJECTION);
        execute(job(FlowNodes.ServiceTaskSendRejection.ELEMENT_ID, instance)); // -> end

        // then
        assertThat(instance)
                .isEnded()
                .hasPassedInOrder(PathWalk.from(FlowNodes.StartEventLeasingRequestReceived.INSTANCE)
                        .then(next -> next.businessRuleTaskCheckCreditRating())
                        .then(next -> next.gatewayIsSolvent())
                        .onto(next -> next.subProcessConcludeContract())
                        .inside(FlowNodes.SubProcessConcludeContract.INSTANCE, start ->
                                PathWalk.from(start.startEventCustomerEligible())
                                        .then(next -> next.serviceTaskSendContract())
                                        .then(next -> next.gatewayAwaitSignature())
                                        .then(next -> next.eventSignatureDeadline())
                                        .end(next -> next.endEventNotSigned()))
                        .interruptedBy(
                                FlowNodes.SubProcessConcludeContract.INSTANCE,
                                next -> next.eventContractNotSigned())
                        .then(next -> next.gatewayRejectionJoin())
                        .then(next -> next.serviceTaskSendRejection())
                        .end(next -> next.endEventApplicationRejected())
                        .getIds())
                .hasNotPassed(FlowNodes.EndEventLeasingActive.ELEMENT_ID);
    }

    @Test
    @DisplayName("not solvent - the DMN routes the application straight to rejection")
    void notSolventTheDmnRoutesTheApplicationStraightToRejection() {
        // given
        // age below 18 cannot sign a leasing contract, so the DMN returns solvent = false
        String businessKey = submit(15, 3500.0);
        ProcessInstance instance = findInstance(engine, businessKey);

        // when
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance)); // the DMN (not solvent) parks on the rejection
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_SEND_REJECTION);
        execute(job(FlowNodes.ServiceTaskSendRejection.ELEMENT_ID, instance)); // -> end

        // then
        assertThat(instance)
                .isEnded()
                .hasPassedInOrder(PathWalk.from(FlowNodes.StartEventLeasingRequestReceived.INSTANCE)
                        .then(next -> next.businessRuleTaskCheckCreditRating())
                        .then(next -> next.gatewayIsSolvent())
                        .then(next -> next.gatewayRejectionJoin())
                        .then(next -> next.serviceTaskSendRejection())
                        .end(next -> next.endEventApplicationRejected())
                        .getIds())
                .hasNotPassed(
                        FlowNodes.SubProcessConcludeContract.ELEMENT_ID,
                        FlowNodes.EndEventLeasingActive.ELEMENT_ID);
    }

    @Test
    @DisplayName("reminder - an unsigned contract gets one reminder before the deadline rejects it")
    void reminderAnUnsignedContractGetsOneReminderBeforeTheDeadlineRejectsIt() {
        // given
        String businessKey = submit(35, 3500.0);
        ProcessInstance instance = findInstance(engine, businessKey);

        // when
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_SEND_CONTRACT);
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance)); // -> signature wait state
        fireTimer(engine, FlowNodes.EventSignatureReminder.INSTANCE); // non-interrupting -> reminder mail
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_SEND_REMINDER_MAIL);
        execute(job(FlowNodes.ServiceTaskSendReminderMail.ELEMENT_ID, instance));
        fireTimer(engine, FlowNodes.EventSignatureDeadline.INSTANCE); // deadline -> escalation -> boundary -> rejection
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_SEND_REJECTION);
        execute(job(FlowNodes.ServiceTaskSendRejection.ELEMENT_ID, instance)); // -> end

        // then
        assertThat(instance)
                .isEnded()
                .hasPassedInOrder(PathWalk.from(FlowNodes.StartEventLeasingRequestReceived.INSTANCE)
                        .then(next -> next.businessRuleTaskCheckCreditRating())
                        .then(next -> next.gatewayIsSolvent())
                        .onto(next -> next.subProcessConcludeContract())
                        .interruptedBy(
                                FlowNodes.SubProcessConcludeContract.INSTANCE,
                                next -> next.eventSignatureReminder())
                        .then(next -> next.serviceTaskSendReminderMail())
                        .end(next -> next.endEventProspectReminded())
                        .getIds())
                .hasPassed(FlowNodes.EndEventApplicationRejected.ELEMENT_ID);
    }

    @Test
    @DisplayName("abort - withdrawing the application compensates the completed steps")
    void abortWithdrawingTheApplicationCompensatesTheCompletedSteps() {
        // given
        String businessKey = submit(35, 3500.0);
        ProcessInstance instance = findInstance(engine, businessKey);

        // when
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_SEND_CONTRACT);
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance)); // -> signature wait state
        correlateMessage(engine, Messages.MIRAVELO_CONTRACT_SIGNED, businessKey);
        execute(job(FlowNodes.EventContractSigned.ELEMENT_ID, instance)); // forks into insurance + bike order
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_ISSUE_INSURANCE_POLICY);
        execute(job(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, instance));
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance));
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_ORDER_BIKE, orderPlaced);
        execute(job(FlowNodes.ServiceTaskOrderBike.ELEMENT_ID, instance));
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance)); // -> handover wait state
        correlateMessage(engine, Messages.MIRAVELO_APPLICATION_WITHDRAWN, businessKey);
        execute(job(FlowNodes.StartEventApplicationWithdrawn.ELEMENT_ID, instance)); // -> compensation
        execute(job(CancelBikeOrderProcessApi.FlowNodes.StartEventCancellationRequired.ELEMENT_ID, calledProcessInstance(instance)));
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_CANCEL_POLICY);
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_CANCEL_CONTRACT);
        // the dealer refuses to cancel the order
        completeExternalTask(
                engine,
                ServiceTasks.BIKE_LEASING_REQUEST_CANCELLATION,
                Map.of(CancelBikeOrderProcessApi.FlowNodes.ServiceTaskRequestCancellation.Variables.CANCELLATION_POSSIBLE.getValue(), false));
        execute(job(CancelBikeOrderProcessApi.FlowNodes.ServiceTaskRequestCancellation.ELEMENT_ID, calledProcessInstance(instance))); // -> parks on the clarify-return task
        completeUserTask(engine, CancelBikeOrderProcessApi.FlowNodes.UserTaskClarifyReturn.ELEMENT_ID);
        execute(job(CancelBikeOrderProcessApi.FlowNodes.UserTaskClarifyReturn.ELEMENT_ID, calledProcessInstance(instance)));
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_BOOK_COSTS);
        execute(job(CancelBikeOrderProcessApi.FlowNodes.ServiceTaskBookCosts.ELEMENT_ID, calledProcessInstance(instance)));
        execute(job(CancelBikeOrderProcessApi.FlowNodes.GatewayJoin.ELEMENT_ID, calledProcessInstance(instance)));
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_SEND_CANCELLATION_CONFIRMATION);
        execute(job(FlowNodes.ServiceTaskSendCancellationConfirmation.ELEMENT_ID, instance)); // -> end cancelled

        // then
        assertThat(instance)
                .isEnded()
                .hasPassed(PathWalk.from(FlowNodes.StartEventLeasingRequestReceived.INSTANCE)
                        .then(next -> next.businessRuleTaskCheckCreditRating())
                        .then(next -> next.gatewayIsSolvent())
                        .onto(next -> next.subProcessConcludeContract())
                        .inside(FlowNodes.SubProcessConcludeContract.INSTANCE, start ->
                                PathWalk.from(start.startEventCustomerEligible())
                                        .then(next -> next.serviceTaskSendContract())
                                        .then(next -> next.gatewayAwaitSignature())
                                        .then(next -> next.eventContractSigned())
                                        .end(next -> next.endEventContractValid()))
                        .then(next -> next.gatewayFork())
                        .then(next -> next.gatewayBikeSourceJoin())
                        .then(next -> next.serviceTaskOrderBike())
                        .then(next -> next.gatewayJoin())
                        .enter(
                                FlowNodes.SubProcessApplicationWithdrawn.INSTANCE,
                                start -> start.startEventApplicationWithdrawn())
                        .then(next -> next.eventReverseApplication())
                        .throwingCompensation(
                                FlowNodes.EventCompensateContract.INSTANCE,
                                next -> next.serviceTaskCancelContract())
                        .throwingCompensation(
                                FlowNodes.EventCompensateInsurance.INSTANCE,
                                next -> next.serviceTaskCancelPolicy())
                        .throwingCompensation(
                                FlowNodes.EventCompensateOrder.INSTANCE,
                                next -> next.callActivityCancelBikeOrder())
                        .then(next -> next.serviceTaskSendCancellationConfirmation())
                        .end(next -> next.endEventApplicationCancelled())
                        .getDistinctIds())
                .hasNotPassed(FlowNodes.EndEventLeasingActive.ELEMENT_ID);
        Assertions.assertThat(cancellationCostBookings(instance)).isEqualTo(1);
    }

    @Test
    @DisplayName("bike unavailable - clarifying an alternative re-orders and leasing becomes active")
    void bikeUnavailableClarifyingAnAlternativeReOrdersAndLeasingBecomesActive() {
        // given
        String businessKey = submit(35, 3500.0);
        ProcessInstance instance = findInstance(engine, businessKey);

        // when
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_SEND_CONTRACT);
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance)); // -> signature wait state
        correlateMessage(engine, Messages.MIRAVELO_CONTRACT_SIGNED, businessKey);
        execute(job(FlowNodes.EventContractSigned.ELEMENT_ID, instance)); // forks into insurance + bike order
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_ISSUE_INSURANCE_POLICY);
        execute(job(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, instance));
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance));
        raiseBpmnErrorOnExternalTask(engine, ServiceTasks.BIKE_LEASING_ORDER_BIKE, Errors.BIKE_UNAVAILABLE); // -> parks on the clarify-alternative task
        acceptAlternative();
        execute(job(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID, instance));
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_ORDER_BIKE, orderPlaced); // the re-order succeeds
        execute(job(FlowNodes.ServiceTaskOrderBike.ELEMENT_ID, instance));
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance)); // -> handover wait state
        correlateMessage(engine, Messages.MIRAVELO_HANDOVER_REPORTED, businessKey);
        execute(job(FlowNodes.EventHandoverReported.ELEMENT_ID, instance)); // -> withdrawal-period timer
        fireTimer(engine, FlowNodes.EventWithdrawalPeriodElapsed.INSTANCE);
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_ACTIVATE_LEASING);
        execute(job(FlowNodes.ServiceTaskActivateLeasing.ELEMENT_ID, instance)); // -> end

        // then
        assertThat(instance)
                .isEnded()
                .hasPassedInOrder(PathWalk.from(FlowNodes.StartEventLeasingRequestReceived.INSTANCE)
                        .then(next -> next.businessRuleTaskCheckCreditRating())
                        .then(next -> next.gatewayIsSolvent())
                        .onto(next -> next.subProcessConcludeContract())
                        .inside(FlowNodes.SubProcessConcludeContract.INSTANCE, start ->
                                PathWalk.from(start.startEventCustomerEligible())
                                        .then(next -> next.serviceTaskSendContract())
                                        .then(next -> next.gatewayAwaitSignature())
                                        .then(next -> next.eventContractSigned())
                                        .end(next -> next.endEventContractValid()))
                        .then(next -> next.gatewayFork())
                        .then(next -> next.gatewayBikeSourceJoin())
                        .then(next -> next.serviceTaskOrderBike())
                        .interruptedBy(FlowNodes.ServiceTaskOrderBike.INSTANCE, next -> next.eventBikeUnavailable())
                        .then(next -> next.userTaskClarifyAlternative())
                        .then(next -> next.gatewayAlternativeFound())
                        .then(next -> next.gatewayBikeSourceJoin())
                        .then(next -> next.serviceTaskOrderBike())
                        .then(next -> next.gatewayJoin())
                        .then(next -> next.eventHandoverReported())
                        .then(next -> next.eventWithdrawalPeriodElapsed())
                        .then(next -> next.serviceTaskActivateLeasing())
                        .end(next -> next.endEventLeasingActive())
                        .getIds())
                .hasNotPassed(
                        FlowNodes.EndEventContractCancelled.ELEMENT_ID,
                        FlowNodes.EndEventApplicationRejected.ELEMENT_ID);
        Assertions.assertThat(bikeOrderedBy(instance)).isEqualTo("BIKE-ALT");
    }

    @Test
    @DisplayName("bike unavailable - declining the alternative reverses contract and policy without cancelling an order")
    void bikeUnavailableDecliningTheAlternativeReversesContractAndPolicyWithoutCancellingAnOrder() {
        // given
        String businessKey = submitUntilBikeUnavailable();
        ProcessInstance instance = findInstance(engine, businessKey);

        // when
        completeUserTask(
                engine,
                FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID,
                Map.of(FlowNodes.UserTaskClarifyAlternative.Variables.ALTERNATIVE_FOUND.getValue(), false));
        execute(job(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID, instance)); // -> compensation
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_CANCEL_POLICY);
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_CANCEL_CONTRACT);
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_SEND_CANCELLATION_CONFIRMATION);
        execute(job(FlowNodes.ServiceTaskConfirmContractCancellation.ELEMENT_ID, instance)); // -> end cancelled

        // then
        assertThat(instance)
                .isEnded()
                .hasPassed(PathWalk.from(FlowNodes.StartEventLeasingRequestReceived.INSTANCE)
                        .then(next -> next.businessRuleTaskCheckCreditRating())
                        .then(next -> next.gatewayIsSolvent())
                        .onto(next -> next.subProcessConcludeContract())
                        .inside(FlowNodes.SubProcessConcludeContract.INSTANCE, start ->
                                PathWalk.from(start.startEventCustomerEligible())
                                        .then(next -> next.serviceTaskSendContract())
                                        .then(next -> next.gatewayAwaitSignature())
                                        .then(next -> next.eventContractSigned())
                                        .end(next -> next.endEventContractValid()))
                        .then(next -> next.gatewayFork())
                        .then(next -> next.gatewayBikeSourceJoin())
                        .then(next -> next.serviceTaskOrderBike())
                        .interruptedBy(
                                FlowNodes.ServiceTaskOrderBike.INSTANCE,
                                next -> next.eventBikeUnavailable())
                        .then(next -> next.userTaskClarifyAlternative())
                        .then(next -> next.gatewayAlternativeFound())
                        .then(next -> next.eventTriggerReversal())
                        .throwingCompensation(
                                FlowNodes.EventCompensateContract.INSTANCE,
                                next -> next.serviceTaskCancelContract())
                        .throwingCompensation(
                                FlowNodes.EventCompensateInsurance.INSTANCE,
                                next -> next.serviceTaskCancelPolicy())
                        .then(next -> next.serviceTaskConfirmContractCancellation())
                        .end(next -> next.endEventContractCancelled())
                        .getDistinctIds())
                .hasNotPassed(
                        FlowNodes.CallActivityCancelBikeOrder.ELEMENT_ID,
                        FlowNodes.EndEventLeasingActive.ELEMENT_ID);
    }

    @Test
    @DisplayName("abort while clarifying an alternative - compensates contract and policy without cancelling an order")
    void abortWhileClarifyingAnAlternativeCompensatesContractAndPolicyWithoutCancellingAnOrder() {
        // given
        String businessKey = submitUntilBikeUnavailable();
        ProcessInstance instance = findInstance(engine, businessKey);

        // when
        correlateMessage(engine, Messages.MIRAVELO_APPLICATION_WITHDRAWN, businessKey);
        execute(job(FlowNodes.StartEventApplicationWithdrawn.ELEMENT_ID, instance)); // -> compensation
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_CANCEL_POLICY);
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_CANCEL_CONTRACT);
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_SEND_CANCELLATION_CONFIRMATION);
        execute(job(FlowNodes.ServiceTaskSendCancellationConfirmation.ELEMENT_ID, instance)); // -> end cancelled

        // then
        assertThat(instance)
                .isEnded()
                .hasPassed(PathWalk.from(FlowNodes.StartEventLeasingRequestReceived.INSTANCE)
                        .then(next -> next.businessRuleTaskCheckCreditRating())
                        .then(next -> next.gatewayIsSolvent())
                        .onto(next -> next.subProcessConcludeContract())
                        .inside(FlowNodes.SubProcessConcludeContract.INSTANCE, start ->
                                PathWalk.from(start.startEventCustomerEligible())
                                        .then(next -> next.serviceTaskSendContract())
                                        .then(next -> next.gatewayAwaitSignature())
                                        .then(next -> next.eventContractSigned())
                                        .end(next -> next.endEventContractValid()))
                        .then(next -> next.gatewayFork())
                        .then(next -> next.gatewayBikeSourceJoin())
                        .then(next -> next.serviceTaskOrderBike())
                        .interruptedBy(
                                FlowNodes.ServiceTaskOrderBike.INSTANCE,
                                next -> next.eventBikeUnavailable())
                        .then(next -> next.userTaskClarifyAlternative())
                        .enter(
                                FlowNodes.SubProcessApplicationWithdrawn.INSTANCE,
                                start -> start.startEventApplicationWithdrawn())
                        .then(next -> next.eventReverseApplication())
                        .throwingCompensation(
                                FlowNodes.EventCompensateContract.INSTANCE,
                                next -> next.serviceTaskCancelContract())
                        .throwingCompensation(
                                FlowNodes.EventCompensateInsurance.INSTANCE,
                                next -> next.serviceTaskCancelPolicy())
                        .then(next -> next.serviceTaskSendCancellationConfirmation())
                        .end(next -> next.endEventApplicationCancelled())
                        .getDistinctIds())
                .hasNotPassed(FlowNodes.CallActivityCancelBikeOrder.ELEMENT_ID);
    }

    @Test
    @DisplayName("abort after an accepted alternative - cancels the one placed order exactly once")
    void abortAfterAnAcceptedAlternativeCancelsTheOnePlacedOrderExactlyOnce() {
        // given
        String businessKey = submitUntilBikeUnavailable();
        ProcessInstance instance = findInstance(engine, businessKey);

        // when
        acceptAlternative();
        execute(job(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID, instance));
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_ORDER_BIKE, orderPlaced); // the re-order succeeds
        execute(job(FlowNodes.ServiceTaskOrderBike.ELEMENT_ID, instance));
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance)); // -> handover wait state
        correlateMessage(engine, Messages.MIRAVELO_APPLICATION_WITHDRAWN, businessKey);
        execute(job(FlowNodes.StartEventApplicationWithdrawn.ELEMENT_ID, instance)); // -> compensation
        execute(job(CancelBikeOrderProcessApi.FlowNodes.StartEventCancellationRequired.ELEMENT_ID, calledProcessInstance(instance)));
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_CANCEL_POLICY);
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_CANCEL_CONTRACT);
        // the dealer cancels the order
        completeExternalTask(
                engine,
                ServiceTasks.BIKE_LEASING_REQUEST_CANCELLATION,
                Map.of(CancelBikeOrderProcessApi.FlowNodes.ServiceTaskRequestCancellation.Variables.CANCELLATION_POSSIBLE.getValue(), true));
        execute(job(CancelBikeOrderProcessApi.FlowNodes.ServiceTaskRequestCancellation.ELEMENT_ID, calledProcessInstance(instance)));
        execute(job(CancelBikeOrderProcessApi.FlowNodes.GatewayJoin.ELEMENT_ID, calledProcessInstance(instance))); // -> order reversed without a return to clarify
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_SEND_CANCELLATION_CONFIRMATION);
        execute(job(FlowNodes.ServiceTaskSendCancellationConfirmation.ELEMENT_ID, instance)); // -> end cancelled

        // then
        assertThat(instance).isEnded().hasPassed(FlowNodes.EndEventApplicationCancelled.ELEMENT_ID);
        long orderCancellations = engine.getHistoryService()
                .createHistoricProcessInstanceQuery()
                .processDefinitionKey(CancelBikeOrderProcessApi.PROCESS_ID.getValue())
                .count();
        Assertions.assertThat(orderCancellations).isEqualTo(1);
        Assertions.assertThat(cancellationCostBookings(instance)).isZero();
    }

    /** Drives a signed, insured application to the clarify-alternative task: the dealer has no bike. */
    private String submitUntilBikeUnavailable() {
        String businessKey = submit(35, 3500.0);
        ProcessInstance instance = findInstance(engine, businessKey);
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_SEND_CONTRACT);
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance)); // -> signature wait state
        correlateMessage(engine, Messages.MIRAVELO_CONTRACT_SIGNED, businessKey);
        execute(job(FlowNodes.EventContractSigned.ELEMENT_ID, instance)); // forks into insurance + bike order
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_ISSUE_INSURANCE_POLICY);
        execute(job(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, instance));
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance));
        raiseBpmnErrorOnExternalTask(engine, ServiceTasks.BIKE_LEASING_ORDER_BIKE, Errors.BIKE_UNAVAILABLE); // -> parks on the clarify-alternative task
        return businessKey;
    }

    private void acceptAlternative() {
        completeUserTask(
                engine,
                FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID,
                Map.of(
                        FlowNodes.UserTaskClarifyAlternative.Variables.ALTERNATIVE_FOUND.getValue(), true,
                        FlowNodes.UserTaskClarifyAlternative.Variables.BIKE_ID.getValue(), "BIKE-ALT"));
    }

    /** How often the order cancellations called by {@code instance} booked the dealer's cancellation costs. */
    private long cancellationCostBookings(ProcessInstance instance) {
        return engine.getHistoryService()
                .createHistoricProcessInstanceQuery()
                .superProcessInstanceId(instance.getId())
                .list()
                .stream()
                .mapToLong(orderCancellation -> engine.getHistoryService()
                        .createHistoricActivityInstanceQuery()
                        .processInstanceId(orderCancellation.getId())
                        .activityId(CancelBikeOrderProcessApi.FlowNodes.ServiceTaskBookCosts.ELEMENT_ID)
                        .count())
                .sum();
    }

    /** The bike {@code instance} carried when it ended — the one its last {@code orderBike} external task handed to the worker. */
    private Object bikeOrderedBy(ProcessInstance instance) {
        return engine.getHistoryService()
                .createHistoricVariableInstanceQuery()
                .processInstanceId(instance.getId())
                .variableName(FlowNodes.ServiceTaskOrderBike.Variables.BIKE_ID.getValue())
                .singleResult()
                .getValue();
    }

    private String submit(int age, double income) {
        return submit(age, income, "BIKE-TEST");
    }

    /** Starts the process through its message start event, keyed by a fresh application business key. */
    private String submit(int age, double income, String bikeId) {
        String businessKey = UUID.randomUUID().toString();
        startLeasing(engine, businessKey, age, income, bikeId);
        return businessKey;
    }
}
