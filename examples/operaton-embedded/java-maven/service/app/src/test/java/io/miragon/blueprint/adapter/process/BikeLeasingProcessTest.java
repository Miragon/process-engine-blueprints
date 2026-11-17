package io.miragon.blueprint.adapter.process;

import static io.miragon.blueprint.adapter.process.ProcessInstanceUtils.findProcessInstance;
import static io.miragon.blueprint.adapter.process.ProcessPathAssertions.inAnyOrder;
import static io.miragon.blueprint.adapter.process.ProcessPathAssertions.inWalkOrder;
import static io.miragon.blueprint.adapter.process.TimerUtils.fireTimer;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.calledProcessInstance;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.execute;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.init;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.job;
import static org.mockito.ArgumentMatchers.any;
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
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
    void happyPathContractSignedBikeAvailableLeasingBecomesActive() {
        // given
        ApplicationId id = submit(35, 3500.0);
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        // when
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance));
        process.correlateContractSigned(id);
        execute(job(FlowNodes.EventContractSigned.ELEMENT_ID, instance)); // forks into insurance + bike order
        execute(job(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, instance));
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskOrderBike.ELEMENT_ID, instance));
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance)); // -> handover wait state
        process.correlateHandoverReported(id);
        execute(job(FlowNodes.EventHandoverReported.ELEMENT_ID, instance)); // -> withdrawal-period timer
        fireTimer(processEngine, FlowNodes.EventWithdrawalPeriodElapsed.INSTANCE);
        execute(job(FlowNodes.EndEventLeasingActive.ELEMENT_ID, instance));

        // then
        assertThat(instance)
                .isEnded()
                .hasPassedInOrder(inWalkOrder(
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
                                .end(next -> next.endEventLeasingActive())))
                .hasPassedInOrder(inWalkOrder(
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
                                .then(next -> next.gatewayJoin())))
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
    void escalationContractNotSignedInTimeIsEscalatedAndRejected() {
        // given
        ApplicationId id = submit(35, 3500.0);
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        // when
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance));
        fireTimer(processEngine, FlowNodes.EventSignatureDeadline.INSTANCE); // deadline -> escalation -> rejection
        execute(job(FlowNodes.ServiceTaskSendRejection.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskSendRejection.ELEMENT_ID, instance));

        // then
        assertThat(instance)
                .isEnded()
                .hasPassedInOrder(inWalkOrder(
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
                                .end(next -> next.endEventApplicationRejected())))
                .hasNotPassed(FlowNodes.EndEventLeasingActive.ELEMENT_ID);

        verify(rejectApplicationUseCase, times(1)).reject(id);
    }

    @Test
    void notSolventTheDmnRoutesTheApplicationStraightToRejection() {
        // given
        // age below 18 cannot sign a leasing contract, so the DMN returns solvent = false
        ApplicationId id = submit(15, 3500.0);
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        // when
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance)); // -> DMN -> not solvent -> rejection
        execute(job(FlowNodes.ServiceTaskSendRejection.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskSendRejection.ELEMENT_ID, instance));

        // then
        assertThat(instance)
                .isEnded()
                .hasPassedInOrder(inWalkOrder(
                        PathWalk.from(FlowNodes.StartEventLeasingRequestReceived.INSTANCE)
                                .then(next -> next.businessRuleTaskCheckCreditRating())
                                .then(next -> next.gatewayIsSolvent())
                                .then(next -> next.gatewayRejectionJoin())
                                .then(next -> next.serviceTaskSendRejection())
                                .end(next -> next.endEventApplicationRejected())))
                .hasNotPassed(FlowNodes.SubProcessConcludeContract.ELEMENT_ID);

        verify(rejectApplicationUseCase, times(1)).reject(id);
        verify(sendContractUseCase, never()).sendContract(any());
    }

    @Test
    void reminderAnUnsignedContractGetsOneReminderBeforeTheDeadlineRejectsIt() {
        // given
        ApplicationId id = submit(35, 3500.0);
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        // when
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance));
        fireTimer(processEngine, FlowNodes.EventSignatureReminder.INSTANCE);
        execute(job(FlowNodes.ServiceTaskSendReminderMail.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskSendReminderMail.ELEMENT_ID, instance));
        fireTimer(processEngine, FlowNodes.EventSignatureDeadline.INSTANCE);
        execute(job(FlowNodes.ServiceTaskSendRejection.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskSendRejection.ELEMENT_ID, instance));

        // then
        assertThat(instance)
                .isEnded()
                .hasPassedInOrder(inWalkOrder(
                        PathWalk.from(FlowNodes.StartEventLeasingRequestReceived.INSTANCE)
                                .then(next -> next.businessRuleTaskCheckCreditRating())
                                .then(next -> next.gatewayIsSolvent())
                                .onto(next -> next.subProcessConcludeContract())
                                .interruptedBy(
                                        FlowNodes.SubProcessConcludeContract.INSTANCE,
                                        next -> next.eventSignatureReminder())
                                .then(next -> next.serviceTaskSendReminderMail())
                                .end(next -> next.endEventProspectReminded())))
                .hasPassed(FlowNodes.EndEventApplicationRejected.ELEMENT_ID);

        verify(sendSignatureReminderUseCase, times(1)).sendSignatureReminder(id);
        verify(rejectApplicationUseCase, times(1)).reject(id);
    }

    @Test
    void abortWithdrawingTheApplicationCompensatesTheCompletedSteps() {
        // given
        when(requestOrderCancellationUseCase.requestCancellation(any())).thenReturn(false);
        ApplicationId id = submit(35, 3500.0);
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        // when
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance));
        process.correlateContractSigned(id);
        execute(job(FlowNodes.EventContractSigned.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, instance));
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskOrderBike.ELEMENT_ID, instance));
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance)); // -> handover wait state
        process.correlateApplicationWithdrawn(id);
        execute(job(FlowNodes.StartEventApplicationWithdrawn.ELEMENT_ID, instance)); // -> compensation
        execute(job(CancelBikeOrderProcessApi.FlowNodes.StartEventCancellationRequired.ELEMENT_ID, calledProcessInstance(instance)));
        execute(job(CancelBikeOrderProcessApi.FlowNodes.ServiceTaskRequestCancellation.ELEMENT_ID, calledProcessInstance(instance)));
        execute(job(CancelBikeOrderProcessApi.FlowNodes.ServiceTaskRequestCancellation.ELEMENT_ID, calledProcessInstance(instance)));
        process.completeReturnClarification(id);
        execute(job(CancelBikeOrderProcessApi.FlowNodes.UserTaskClarifyReturn.ELEMENT_ID, calledProcessInstance(instance)));
        execute(job(CancelBikeOrderProcessApi.FlowNodes.ServiceTaskBookCosts.ELEMENT_ID, calledProcessInstance(instance)));
        execute(job(CancelBikeOrderProcessApi.FlowNodes.ServiceTaskBookCosts.ELEMENT_ID, calledProcessInstance(instance)));
        execute(job(CancelBikeOrderProcessApi.FlowNodes.GatewayJoin.ELEMENT_ID, calledProcessInstance(instance)));
        execute(job(FlowNodes.ServiceTaskSendCancellationConfirmation.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskSendCancellationConfirmation.ELEMENT_ID, instance));

        // then
        assertThat(instance)
                .isEnded()
                .hasPassed(inAnyOrder(
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
                                .end(next -> next.endEventApplicationCancelled())))
                .hasNotPassed(FlowNodes.EndEventLeasingActive.ELEMENT_ID);

        verify(cancelContractUseCase, times(1)).cancelContract(id);
        verify(cancelInsurancePolicyUseCase, times(1)).cancelPolicy(id);
        verify(sendCancellationConfirmationUseCase, times(1)).sendCancellationConfirmation(id);
        verify(bookCancellationCostsUseCase, times(1)).bookCosts(new OrderId("ORDER-1"));
    }

    @Test
    void bikeUnavailableClarifyingAnAlternativeReOrdersAndLeasingBecomesActive() {
        // given
        // the first order finds the requested bike unavailable, the re-order after the alternative succeeds
        when(orderBikeUseCase.orderBike(any(), any()))
                .thenThrow(new BikeUnavailableException(new BikeId("BIKE-TEST")))
                .thenReturn(new OrderId("ORDER-2"));
        ApplicationId id = submit(35, 3500.0);
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        // when
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance));
        process.correlateContractSigned(id);
        execute(job(FlowNodes.EventContractSigned.ELEMENT_ID, instance)); // forks into insurance + bike order
        execute(job(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, instance));
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskOrderBike.ELEMENT_ID, instance)); // unavailable -> parks on clarify-alternative
        // the alternative is clarified from the outside — the "external" completion of the user task
        process.completeAlternativeClarification(id, true, new BikeId("BIKE-ALT"));
        execute(job(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskOrderBike.ELEMENT_ID, instance)); // re-order succeeds
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance)); // -> handover wait state
        process.correlateHandoverReported(id);
        execute(job(FlowNodes.EventHandoverReported.ELEMENT_ID, instance));
        fireTimer(processEngine, FlowNodes.EventWithdrawalPeriodElapsed.INSTANCE);
        execute(job(FlowNodes.EndEventLeasingActive.ELEMENT_ID, instance));

        // then
        assertThat(instance)
                .isEnded()
                .hasPassedInOrder(inWalkOrder(
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
                                .end(next -> next.endEventLeasingActive())))
                .hasNotPassed(
                        FlowNodes.EndEventContractCancelled.ELEMENT_ID,
                        FlowNodes.EndEventApplicationRejected.ELEMENT_ID);

        InOrder orders = inOrder(orderBikeUseCase);
        orders.verify(orderBikeUseCase).orderBike(id, new BikeId("BIKE-TEST"));
        orders.verify(orderBikeUseCase).orderBike(id, new BikeId("BIKE-ALT"));
        verify(activateLeasingUseCase, times(1)).activate(id);
    }

    @Test
    void bikeUnavailableDecliningTheAlternativeReversesContractAndPolicyWithoutCancellingAnOrder() {
        // given
        ApplicationId id = submitUntilBikeUnavailable();
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        // when
        process.completeAlternativeClarification(id, false);
        execute(job(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID, instance)); // -> compensation
        execute(job(FlowNodes.ServiceTaskConfirmContractCancellation.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskConfirmContractCancellation.ELEMENT_ID, instance)); // -> end cancelled

        // then
        assertThat(instance)
                .isEnded()
                .hasPassed(inAnyOrder(
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
                                .interruptedBy(FlowNodes.ServiceTaskOrderBike.INSTANCE, next -> next.eventBikeUnavailable())
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
                                .end(next -> next.endEventContractCancelled())))
                .hasNotPassed(
                        FlowNodes.CallActivityCancelBikeOrder.ELEMENT_ID,
                        FlowNodes.EndEventLeasingActive.ELEMENT_ID);

        verify(cancelContractUseCase, times(1)).cancelContract(id);
        verify(cancelInsurancePolicyUseCase, times(1)).cancelPolicy(id);
        verify(sendCancellationConfirmationUseCase, times(1)).sendCancellationConfirmation(id);
        verify(requestOrderCancellationUseCase, never()).requestCancellation(any());
    }

    @Test
    void abortWhileClarifyingAnAlternativeCompensatesContractAndPolicyWithoutCancellingAnOrder() {
        // given
        ApplicationId id = submitUntilBikeUnavailable();
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        // when
        process.correlateApplicationWithdrawn(id);
        execute(job(FlowNodes.StartEventApplicationWithdrawn.ELEMENT_ID, instance)); // -> compensation
        execute(job(FlowNodes.ServiceTaskSendCancellationConfirmation.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskSendCancellationConfirmation.ELEMENT_ID, instance)); // -> end cancelled

        // then
        assertThat(instance)
                .isEnded()
                .hasPassed(inAnyOrder(
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
                                .interruptedBy(FlowNodes.ServiceTaskOrderBike.INSTANCE, next -> next.eventBikeUnavailable())
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
                                .end(next -> next.endEventApplicationCancelled())))
                .hasNotPassed(FlowNodes.CallActivityCancelBikeOrder.ELEMENT_ID);

        verify(sendCancellationConfirmationUseCase, times(1)).sendCancellationConfirmation(id);
        verify(requestOrderCancellationUseCase, never()).requestCancellation(any());
    }

    @Test
    void abortAfterAnAcceptedAlternativeCancelsTheOnePlacedOrderExactlyOnce() {
        // given
        when(requestOrderCancellationUseCase.requestCancellation(any())).thenReturn(true);
        ApplicationId id = submitUntilBikeUnavailable();
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        // when
        process.completeAlternativeClarification(id, true, new BikeId("BIKE-ALT"));
        execute(job(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskOrderBike.ELEMENT_ID, instance)); // re-order succeeds
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance)); // -> handover wait state
        process.correlateApplicationWithdrawn(id);
        execute(job(FlowNodes.StartEventApplicationWithdrawn.ELEMENT_ID, instance)); // -> compensation
        execute(job(CancelBikeOrderProcessApi.FlowNodes.StartEventCancellationRequired.ELEMENT_ID, calledProcessInstance(instance)));
        execute(job(CancelBikeOrderProcessApi.FlowNodes.ServiceTaskRequestCancellation.ELEMENT_ID, calledProcessInstance(instance)));
        execute(job(CancelBikeOrderProcessApi.FlowNodes.ServiceTaskRequestCancellation.ELEMENT_ID, calledProcessInstance(instance))); // the dealer cancels the order
        execute(job(CancelBikeOrderProcessApi.FlowNodes.GatewayJoin.ELEMENT_ID, calledProcessInstance(instance))); // -> order reversed without a return to clarify
        execute(job(FlowNodes.ServiceTaskSendCancellationConfirmation.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskSendCancellationConfirmation.ELEMENT_ID, instance));

        // then
        assertThat(instance).isEnded().hasPassed(FlowNodes.EndEventApplicationCancelled.ELEMENT_ID);
        long orderCancellations = historyService
                .createHistoricProcessInstanceQuery()
                .processDefinitionKey(CancelBikeOrderProcessApi.PROCESS_ID.getValue())
                .superProcessInstanceId(instance.getId())
                .count();
        Assertions.assertThat(orderCancellations).isEqualTo(1);
        verify(bookCancellationCostsUseCase, never()).bookCosts(any());
    }

    /** Drives a signed, insured application to the clarify-alternative task: the dealer has no bike. */
    private ApplicationId submitUntilBikeUnavailable() {
        when(orderBikeUseCase.orderBike(any(), any()))
                .thenThrow(new BikeUnavailableException(new BikeId("BIKE-TEST")))
                .thenReturn(new OrderId("ORDER-2"));

        ApplicationId id = submit(35, 3500.0);
        ProcessInstance instance = findProcessInstance(runtimeService, id);
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance));
        process.correlateContractSigned(id);
        execute(job(FlowNodes.EventContractSigned.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, instance));
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskOrderBike.ELEMENT_ID, instance)); // unavailable -> parks on clarify-alternative
        return id;
    }

    private ApplicationId submit(int age, double income) {
        LeasingApplication application = new LeasingApplication(
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
