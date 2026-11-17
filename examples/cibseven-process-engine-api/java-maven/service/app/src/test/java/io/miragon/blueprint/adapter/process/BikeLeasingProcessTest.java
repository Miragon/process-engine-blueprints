package io.miragon.blueprint.adapter.process;

import static io.miragon.blueprint.adapter.process.ProcessInstanceUtils.findProcessInstance;
import static io.miragon.blueprint.adapter.process.TimerUtils.fireTimer;
import static io.miragon.blueprint.adapter.process.WorkerUtils.awaitWorkers;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.calledProcessInstance;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.execute;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.init;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.job;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes;
import io.miragon.blueprint.application.port.inbound.ActivateLeasingUseCase;
import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase;
import io.miragon.blueprint.application.port.inbound.CancelContractUseCase;
import io.miragon.blueprint.application.port.inbound.CancelInsurancePolicyUseCase;
import io.miragon.blueprint.application.port.inbound.IssueInsurancePolicyUseCase;
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase;
import io.miragon.blueprint.application.port.inbound.RejectApplicationUseCase;
import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase;
import io.miragon.blueprint.application.port.inbound.SendCancellationConfirmationUseCase;
import io.miragon.blueprint.application.port.inbound.SendContractUseCase;
import io.miragon.blueprint.application.port.inbound.SendSignatureReminderUseCase;
import io.miragon.blueprint.application.port.outbound.LeasingProcess;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.BikeUnavailableException;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.CustomerName;
import io.miragon.blueprint.domain.leasing.Email;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import io.miragon.bpmn.runtime.path.PathWalk;
import java.time.LocalDateTime;
import org.assertj.core.api.Assertions;
import org.cibseven.bpm.engine.HistoryService;
import org.cibseven.bpm.engine.ProcessEngine;
import org.cibseven.bpm.engine.RuntimeService;
import org.cibseven.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Drives the deployed model end-to-end. Unlike the classic-delegate blueprint, the service tasks are
 * external tasks completed by the real, asynchronously polling {@code @ProcessEngineWorker} beans (which
 * call the mocked use cases). The test therefore waits for the workers with
 * {@link WorkerUtils#awaitWorkers(ProcessEngine)} and runs each async continuation with its own
 * {@code execute(job(...))}, one job per call, and correlates messages / fires timers / completes the user
 * task through the {@link LeasingProcess} port.
 */
@SpringBootTest
@ActiveProfiles("test")
class BikeLeasingProcessTest {

    @Autowired
    private LeasingProcess process;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private ProcessEngine processEngine;

    @MockitoBean
    private RejectApplicationUseCase rejectApplicationUseCase;

    @MockitoBean
    private SendContractUseCase sendContractUseCase;

    @MockitoBean
    private CancelContractUseCase cancelContractUseCase;

    @MockitoBean
    private IssueInsurancePolicyUseCase issueInsurancePolicyUseCase;

    @MockitoBean
    private CancelInsurancePolicyUseCase cancelInsurancePolicyUseCase;

    @MockitoBean
    private SendSignatureReminderUseCase sendSignatureReminderUseCase;

    @MockitoBean
    private SendCancellationConfirmationUseCase sendCancellationConfirmationUseCase;

    @MockitoBean
    private RequestOrderCancellationUseCase requestOrderCancellationUseCase;

    @MockitoBean
    private BookCancellationCostsUseCase bookCancellationCostsUseCase;

    @MockitoBean
    private OrderBikeUseCase orderBikeUseCase;

    @MockitoBean
    private ActivateLeasingUseCase activateLeasingUseCase;

    @BeforeEach
    void setUp() {
        init(processEngine);
        when(orderBikeUseCase.orderBike(any(), any())).thenReturn(new OrderId("ORDER-1"));
    }

    @Test
    @DisplayName("happy path - contract signed, bike available, leasing becomes active")
    void happyPathContractSignedBikeAvailableLeasingBecomesActive() throws InterruptedException {
        // given
        ApplicationId id = submit(35, 3500.0);
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        // when
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        awaitWorkers(processEngine);
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance)); // parks on the signature wait state
        process.correlateContractSigned(id);
        execute(job(FlowNodes.EventContractSigned.ELEMENT_ID, instance)); // forks into insurance + bike order
        awaitWorkers(processEngine);
        execute(job(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, instance));
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskOrderBike.ELEMENT_ID, instance));
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance)); // -> handover wait state
        process.correlateHandoverReported(id);
        execute(job(FlowNodes.EventHandoverReported.ELEMENT_ID, instance)); // -> withdrawal-period timer
        fireTimer(processEngine, FlowNodes.EventWithdrawalPeriodElapsed.INSTANCE);
        awaitWorkers(processEngine);
        execute(job(FlowNodes.ServiceTaskActivateLeasing.ELEMENT_ID, instance));

        // then
        assertThat(instance)
            .isEnded()
            .hasPassedInOrder(
                PathWalk.from(FlowNodes.StartEventLeasingRequestReceived.INSTANCE)
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
            .hasPassedInOrder(
                PathWalk.from(FlowNodes.StartEventLeasingRequestReceived.INSTANCE)
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

        verify(sendContractUseCase, times(1)).sendContract(id);
        verify(issueInsurancePolicyUseCase, times(1)).issuePolicy(id);
        verify(activateLeasingUseCase, times(1)).activate(id);
    }

    @Test
    @DisplayName("escalation - contract not signed in time is escalated and rejected")
    void escalationContractNotSignedInTimeIsEscalatedAndRejected() throws InterruptedException {
        // given
        ApplicationId id = submit(35, 3500.0);
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        // when
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        awaitWorkers(processEngine);
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance)); // parks on the signature wait state
        fireTimer(processEngine, FlowNodes.EventSignatureDeadline.INSTANCE); // deadline -> escalation -> rejection
        awaitWorkers(processEngine);
        execute(job(FlowNodes.ServiceTaskSendRejection.ELEMENT_ID, instance));

        // then
        assertThat(instance)
            .isEnded()
            .hasPassedInOrder(
                PathWalk.from(FlowNodes.StartEventLeasingRequestReceived.INSTANCE)
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

        verify(rejectApplicationUseCase, times(1)).reject(id);
    }

    @Test
    @DisplayName("not solvent - the DMN routes the application straight to rejection")
    void notSolventTheDmnRoutesTheApplicationStraightToRejection() throws InterruptedException {
        // given
        // age below 18 cannot sign a leasing contract, so the DMN returns solvent = false
        ApplicationId id = submit(15, 3500.0);
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        // when
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance)); // DMN -> not solvent -> rejection
        awaitWorkers(processEngine);
        execute(job(FlowNodes.ServiceTaskSendRejection.ELEMENT_ID, instance)); // -> end

        // then
        assertThat(instance)
            .isEnded()
            .hasPassedInOrder(
                PathWalk.from(FlowNodes.StartEventLeasingRequestReceived.INSTANCE)
                    .then(next -> next.businessRuleTaskCheckCreditRating())
                    .then(next -> next.gatewayIsSolvent())
                    .then(next -> next.gatewayRejectionJoin())
                    .then(next -> next.serviceTaskSendRejection())
                    .end(next -> next.endEventApplicationRejected())
                    .getIds())
            .hasNotPassed(FlowNodes.SubProcessConcludeContract.ELEMENT_ID);

        verify(rejectApplicationUseCase, times(1)).reject(id);
        verify(sendContractUseCase, never()).sendContract(any());
    }

    @Test
    @DisplayName("reminder - an unsigned contract gets one reminder before the deadline rejects it")
    void reminderAnUnsignedContractGetsOneReminderBeforeTheDeadlineRejectsIt() throws InterruptedException {
        // given
        ApplicationId id = submit(35, 3500.0);
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        // when
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        awaitWorkers(processEngine);
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance)); // parks on the signature wait state
        fireTimer(processEngine, FlowNodes.EventSignatureReminder.INSTANCE);
        awaitWorkers(processEngine); // the worker sends the reminder mail
        execute(job(FlowNodes.ServiceTaskSendReminderMail.ELEMENT_ID, instance));
        fireTimer(processEngine, FlowNodes.EventSignatureDeadline.INSTANCE);
        awaitWorkers(processEngine);
        execute(job(FlowNodes.ServiceTaskSendRejection.ELEMENT_ID, instance));

        // then
        assertThat(instance)
            .isEnded()
            .hasPassedInOrder(
                PathWalk.from(FlowNodes.StartEventLeasingRequestReceived.INSTANCE)
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

        verify(sendSignatureReminderUseCase, times(1)).sendSignatureReminder(id);
        verify(rejectApplicationUseCase, times(1)).reject(id);
    }

    @Test
    @DisplayName("abort - withdrawing the application compensates the completed steps")
    void abortWithdrawingTheApplicationCompensatesTheCompletedSteps() throws InterruptedException {
        // given
        when(requestOrderCancellationUseCase.requestCancellation(any())).thenReturn(false);
        ApplicationId id = submit(35, 3500.0);
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        // when
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        awaitWorkers(processEngine);
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance)); // parks on the signature wait state
        process.correlateContractSigned(id);
        execute(job(FlowNodes.EventContractSigned.ELEMENT_ID, instance)); // forks into insurance + bike order
        awaitWorkers(processEngine);
        execute(job(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, instance));
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskOrderBike.ELEMENT_ID, instance));
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance)); // -> handover wait state
        process.correlateApplicationWithdrawn(id);
        execute(job(FlowNodes.StartEventApplicationWithdrawn.ELEMENT_ID, instance)); // -> compensation
        awaitWorkers(processEngine);
        execute(job(CancelBikeOrderProcessApi.FlowNodes.StartEventCancellationRequired.ELEMENT_ID, calledProcessInstance(instance)));
        awaitWorkers(processEngine);
        execute(job(CancelBikeOrderProcessApi.FlowNodes.ServiceTaskRequestCancellation.ELEMENT_ID, calledProcessInstance(instance)));
        process.completeReturnClarification(id);
        execute(job(CancelBikeOrderProcessApi.FlowNodes.UserTaskClarifyReturn.ELEMENT_ID, calledProcessInstance(instance)));
        awaitWorkers(processEngine);
        execute(job(CancelBikeOrderProcessApi.FlowNodes.ServiceTaskBookCosts.ELEMENT_ID, calledProcessInstance(instance)));
        awaitWorkers(processEngine);
        execute(job(FlowNodes.ServiceTaskSendCancellationConfirmation.ELEMENT_ID, instance));

        // then
        assertThat(instance)
            .isEnded()
            .hasPassed(
                PathWalk.from(FlowNodes.StartEventLeasingRequestReceived.INSTANCE)
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
                    .enter(FlowNodes.SubProcessApplicationWithdrawn.INSTANCE, start -> start.startEventApplicationWithdrawn())
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

        // External-task delivery is at-least-once: while compensation runs several handlers in
        // parallel, an async continuation firing on the test thread can force an optimistic-locking
        // retry of a handler that already ran, so a worker's use case may be invoked more than once
        // (workers are expected to be idempotent). The process still ends correctly, as asserted above.
        verify(cancelContractUseCase, atLeastOnce()).cancelContract(id);
        verify(cancelInsurancePolicyUseCase, atLeastOnce()).cancelPolicy(id);
        verify(sendCancellationConfirmationUseCase, atLeastOnce()).sendCancellationConfirmation(id);
        verify(bookCancellationCostsUseCase, times(1)).bookCosts(new OrderId("ORDER-1"));
    }

    @Test
    @DisplayName("bike unavailable - clarifying an alternative re-orders and leasing becomes active")
    void bikeUnavailableClarifyingAnAlternativeReOrdersAndLeasingBecomesActive()
        throws InterruptedException {
        // given
        ApplicationId id = submitUntilBikeUnavailable();
        ProcessInstance instance = findProcessInstance(runtimeService, id);
        doReturn(new OrderId("ORDER-2")).when(orderBikeUseCase).orderBike(any(), any()); // the alternative is in stock

        // when
        // the alternative is clarified from the outside — the "external" completion of the user task
        process.completeAlternativeClarification(id, true, new BikeId("BIKE-ALT"));
        execute(job(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID, instance));
        awaitWorkers(processEngine);
        execute(job(FlowNodes.ServiceTaskOrderBike.ELEMENT_ID, instance)); // re-order succeeds
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance)); // -> handover wait state
        process.correlateHandoverReported(id);
        execute(job(FlowNodes.EventHandoverReported.ELEMENT_ID, instance)); // -> withdrawal-period timer
        fireTimer(processEngine, FlowNodes.EventWithdrawalPeriodElapsed.INSTANCE);
        awaitWorkers(processEngine);
        execute(job(FlowNodes.ServiceTaskActivateLeasing.ELEMENT_ID, instance));

        // then
        assertThat(instance)
            .isEnded()
            .hasPassedInOrder(
                PathWalk.from(FlowNodes.StartEventLeasingRequestReceived.INSTANCE)
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

        InOrder orders = inOrder(orderBikeUseCase);
        orders.verify(orderBikeUseCase).orderBike(id, new BikeId("BIKE-TEST"));
        orders.verify(orderBikeUseCase).orderBike(id, new BikeId("BIKE-ALT"));
    }

    @Test
    @DisplayName("bike unavailable - declining the alternative reverses contract and policy without cancelling an order")
    void bikeUnavailableDecliningTheAlternativeReversesContractAndPolicyWithoutCancellingAnOrder()
        throws InterruptedException {
        // given
        ApplicationId id = submitUntilBikeUnavailable();
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        // when
        process.completeAlternativeClarification(id, false, null);
        execute(job(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID, instance)); // -> compensation
        awaitWorkers(processEngine);
        execute(job(FlowNodes.ServiceTaskConfirmContractCancellation.ELEMENT_ID, instance)); // -> end cancelled

        // then
        assertThat(instance)
            .isEnded()
            .hasPassed(
                PathWalk.from(FlowNodes.StartEventLeasingRequestReceived.INSTANCE)
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

        verify(sendCancellationConfirmationUseCase, atLeastOnce()).sendCancellationConfirmation(id);
        verify(requestOrderCancellationUseCase, never()).requestCancellation(any());
    }

    @Test
    @DisplayName("abort while clarifying an alternative - compensates contract and policy without cancelling an order")
    void abortWhileClarifyingAnAlternativeCompensatesContractAndPolicyWithoutCancellingAnOrder()
        throws InterruptedException {
        // given
        ApplicationId id = submitUntilBikeUnavailable();
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        // when
        process.correlateApplicationWithdrawn(id);
        execute(job(FlowNodes.StartEventApplicationWithdrawn.ELEMENT_ID, instance)); // -> compensation
        awaitWorkers(processEngine);
        execute(job(FlowNodes.ServiceTaskSendCancellationConfirmation.ELEMENT_ID, instance)); // -> end cancelled

        // then
        assertThat(instance)
            .isEnded()
            .hasPassed(
                PathWalk.from(FlowNodes.StartEventLeasingRequestReceived.INSTANCE)
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
                    .enter(FlowNodes.SubProcessApplicationWithdrawn.INSTANCE, start -> start.startEventApplicationWithdrawn())
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

        verify(requestOrderCancellationUseCase, never()).requestCancellation(any());
    }

    @Test
    @DisplayName("abort after an accepted alternative - cancels the one placed order exactly once")
    void abortAfterAnAcceptedAlternativeCancelsTheOnePlacedOrderExactlyOnce()
        throws InterruptedException {
        // given
        when(requestOrderCancellationUseCase.requestCancellation(any())).thenReturn(true);
        ApplicationId id = submitUntilBikeUnavailable();
        ProcessInstance instance = findProcessInstance(runtimeService, id);
        doReturn(new OrderId("ORDER-2")).when(orderBikeUseCase).orderBike(any(), any()); // the alternative is in stock

        // when
        process.completeAlternativeClarification(id, true, new BikeId("BIKE-ALT"));
        execute(job(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID, instance));
        awaitWorkers(processEngine);
        execute(job(FlowNodes.ServiceTaskOrderBike.ELEMENT_ID, instance)); // re-order succeeds
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance)); // -> handover wait state
        process.correlateApplicationWithdrawn(id);
        execute(job(FlowNodes.StartEventApplicationWithdrawn.ELEMENT_ID, instance)); // -> compensation
        awaitWorkers(processEngine);
        execute(job(CancelBikeOrderProcessApi.FlowNodes.StartEventCancellationRequired.ELEMENT_ID, calledProcessInstance(instance)));
        awaitWorkers(processEngine);
        execute(job(CancelBikeOrderProcessApi.FlowNodes.ServiceTaskRequestCancellation.ELEMENT_ID, calledProcessInstance(instance))); // the dealer cancels the order -> order reversed without a return to clarify
        awaitWorkers(processEngine);
        execute(job(FlowNodes.ServiceTaskSendCancellationConfirmation.ELEMENT_ID, instance));

        // then
        assertThat(instance).isEnded().hasPassed(FlowNodes.EndEventApplicationCancelled.ELEMENT_ID);
        long orderCancellations =
            historyService
                .createHistoricProcessInstanceQuery()
                .processDefinitionKey(CancelBikeOrderProcessApi.PROCESS_ID.getValue())
                .superProcessInstanceId(instance.getId())
                .count();
        Assertions.assertThat(orderCancellations).isEqualTo(1);
        verify(bookCancellationCostsUseCase, never()).bookCosts(any());
    }

    /** Drives a signed, insured application to the clarify-alternative task: the dealer has no bike. */
    private ApplicationId submitUntilBikeUnavailable() throws InterruptedException {
        when(orderBikeUseCase.orderBike(any(), any())).thenThrow(new BikeUnavailableException(new BikeId("BIKE-TEST")));

        ApplicationId id = submit(35, 3500.0);
        ProcessInstance instance = findProcessInstance(runtimeService, id);
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        awaitWorkers(processEngine);
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance)); // parks on the signature wait state
        process.correlateContractSigned(id);
        execute(job(FlowNodes.EventContractSigned.ELEMENT_ID, instance)); // fork -> order raises bikeUnavailable -> parks on clarify-alternative
        awaitWorkers(processEngine);
        execute(job(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, instance));
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance));
        return id;
    }


    private ApplicationId submit(int age, double income) {
        LeasingApplication application =
            new LeasingApplication(
                ApplicationId.newId(),
                new CustomerName("Test Customer"),
                new Email("test@example.com"),
                age,
                income,
                new BikeId("BIKE-TEST"),
                LeasingStatus.RECEIVED,
                LocalDateTime.now(),
                null,
                null);
        process.submitRequest(application);
        return application.id();
    }
}
