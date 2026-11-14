package io.miragon.blueprint.adapter.process;

import static io.miragon.blueprint.adapter.process.ProcessAwaits.awaitEnded;
import static io.miragon.blueprint.adapter.process.ProcessAwaits.awaitPassed;
import static io.miragon.blueprint.adapter.process.ProcessAwaits.awaitUserTask;
import static io.miragon.blueprint.adapter.process.ProcessAwaits.awaitWaitingAt;
import static io.miragon.blueprint.adapter.process.ProcessAwaits.fireTimerAndContinueInBackground;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.BikeUnavailableException;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.bpmn.runtime.path.PathWalk;
import org.assertj.core.api.Assertions;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration process test of the {@code bike-leasing} model, running as in production: the job executor
 * runs the async continuations and the external-task client polls {@code /engine-rest} with every worker
 * subscribed. The test only acts where a person or a partner would — messages and user tasks through the
 * real process adapter, timers fired early — and waits for the process to reach the next wait state.
 */
@TestPropertySource(properties = {
    "operaton.bpm.job-execution.enabled=true",
    "operaton.bpm.job-execution.wait-time-in-millis=50",
    "operaton.bpm.job-execution.max-wait=200",
})
class BikeLeasingPollingIntegrationProcessTest extends InMemoryEngineIntegrationTest {

    @Test
    @DisplayName("happy path - contract signed, bike available, leasing becomes active")
    void happyPathContractSignedBikeAvailableLeasingBecomesActive() {
        ApplicationId id = submit(35, 3500.0);
        ProcessInstance instance = instanceOf(id);
        awaitWaitingAt(instance, FlowNodes.GatewayAwaitSignature.INSTANCE);

        process.correlateContractSigned(id);
        awaitWaitingAt(instance, FlowNodes.EventHandoverReported.INSTANCE);

        process.correlateHandoverReported(id);
        awaitWaitingAt(instance, FlowNodes.EventWithdrawalPeriodElapsed.INSTANCE);
        fireTimerAndContinueInBackground(engine, FlowNodes.EventWithdrawalPeriodElapsed.INSTANCE);
        awaitEnded(instance);

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
        String orderIdVariable = FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.getValue();
        assertThat(instance).variables().containsEntry(orderIdVariable, "ORDER-1");

        verify(sendContractUseCase).sendContract(id);
        verify(issueInsurancePolicyUseCase).issuePolicy(id);
        verify(orderBikeUseCase).orderBike(id, REQUESTED_BIKE);
        verify(activateLeasingUseCase).activate(id);
    }

    @Test
    @DisplayName("escalation - contract not signed in time is escalated and rejected")
    void escalationContractNotSignedInTimeIsEscalatedAndRejected() {
        ApplicationId id = submit(35, 3500.0);
        ProcessInstance instance = instanceOf(id);
        awaitWaitingAt(instance, FlowNodes.GatewayAwaitSignature.INSTANCE);

        fireTimerAndContinueInBackground(engine, FlowNodes.EventSignatureDeadline.INSTANCE);
        awaitEnded(instance);

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

        verify(rejectApplicationUseCase).reject(id);
    }

    @Test
    @DisplayName("not solvent - the DMN routes the application straight to rejection")
    void notSolventTheDmnRoutesTheApplicationStraightToRejection() {
        ApplicationId id = submit(15, 3500.0);
        ProcessInstance instance = instanceOf(id);
        awaitEnded(instance);

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

        verify(rejectApplicationUseCase).reject(id);
        verify(sendContractUseCase, never()).sendContract(any());
    }

    @Test
    @DisplayName("reminder - an unsigned contract gets one reminder before the deadline rejects it")
    void reminderAnUnsignedContractGetsOneReminderBeforeTheDeadlineRejectsIt() {
        ApplicationId id = submit(35, 3500.0);
        ProcessInstance instance = instanceOf(id);
        awaitWaitingAt(instance, FlowNodes.GatewayAwaitSignature.INSTANCE);

        fireTimerAndContinueInBackground(engine, FlowNodes.EventSignatureReminder.INSTANCE);
        awaitPassed(instance, FlowNodes.EndEventProspectReminded.INSTANCE);
        assertThat(instance).isWaitingAt(FlowNodes.GatewayAwaitSignature.ELEMENT_ID);

        fireTimerAndContinueInBackground(engine, FlowNodes.EventSignatureDeadline.INSTANCE);
        awaitEnded(instance);

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

        verify(sendSignatureReminderUseCase).sendSignatureReminder(id);
        verify(rejectApplicationUseCase).reject(id);
    }

    @Test
    @DisplayName("abort - withdrawing the application compensates the completed steps")
    void abortWithdrawingTheApplicationCompensatesTheCompletedSteps() {
        when(requestOrderCancellationUseCase.requestCancellation(any())).thenReturn(false);

        ApplicationId id = submitUntilHandover();
        ProcessInstance instance = instanceOf(id);

        process.correlateApplicationWithdrawn(id);
        awaitUserTask(engine, CancelBikeOrderProcessApi.FlowNodes.UserTaskClarifyReturn.ELEMENT_ID);
        process.completeReturnClarification(id);
        awaitEnded(instance);

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

        verify(cancelContractUseCase).cancelContract(id);
        verify(cancelInsurancePolicyUseCase).cancelPolicy(id);
        verify(requestOrderCancellationUseCase).requestCancellation(new OrderId("ORDER-1"));
        verify(bookCancellationCostsUseCase).bookCosts(new OrderId("ORDER-1"));
        verify(sendCancellationConfirmationUseCase).sendCancellationConfirmation(id);
    }

    @Test
    @DisplayName("bike unavailable - clarifying an alternative re-orders and leasing becomes active")
    void bikeUnavailableClarifyingAnAlternativeReOrdersAndLeasingBecomesActive() {
        ApplicationId id = submitUntilBikeUnavailable();
        ProcessInstance instance = instanceOf(id);

        process.completeAlternativeClarification(id, true, new BikeId("BIKE-ALT"));
        awaitWaitingAt(instance, FlowNodes.EventHandoverReported.INSTANCE);

        process.correlateHandoverReported(id);
        awaitWaitingAt(instance, FlowNodes.EventWithdrawalPeriodElapsed.INSTANCE);
        fireTimerAndContinueInBackground(engine, FlowNodes.EventWithdrawalPeriodElapsed.INSTANCE);
        awaitEnded(instance);

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

        verify(orderBikeUseCase).orderBike(id, REQUESTED_BIKE);
        verify(orderBikeUseCase).orderBike(id, new BikeId("BIKE-ALT"));
        verify(activateLeasingUseCase).activate(id);
    }

    @Test
    @DisplayName("bike unavailable - declining the alternative reverses contract and policy without cancelling an order")
    void bikeUnavailableDecliningTheAlternativeReversesContractAndPolicyWithoutCancellingAnOrder() {
        ApplicationId id = submitUntilBikeUnavailable();
        ProcessInstance instance = instanceOf(id);

        process.completeAlternativeClarification(id, false);
        awaitEnded(instance);

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

        verify(cancelContractUseCase).cancelContract(id);
        verify(cancelInsurancePolicyUseCase).cancelPolicy(id);
        verify(requestOrderCancellationUseCase, never()).requestCancellation(any());
    }

    @Test
    @DisplayName("abort while clarifying an alternative - compensates contract and policy without cancelling an order")
    void abortWhileClarifyingAnAlternativeCompensatesContractAndPolicyWithoutCancellingAnOrder() {
        ApplicationId id = submitUntilBikeUnavailable();
        ProcessInstance instance = instanceOf(id);

        process.correlateApplicationWithdrawn(id);
        awaitEnded(instance);

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

        verify(sendCancellationConfirmationUseCase).sendCancellationConfirmation(id);
        verify(requestOrderCancellationUseCase, never()).requestCancellation(any());
    }

    @Test
    @DisplayName("abort after an accepted alternative - cancels the one placed order exactly once")
    void abortAfterAnAcceptedAlternativeCancelsTheOnePlacedOrderExactlyOnce() {
        when(requestOrderCancellationUseCase.requestCancellation(any())).thenReturn(true);

        ApplicationId id = submitUntilBikeUnavailable();
        ProcessInstance instance = instanceOf(id);

        process.completeAlternativeClarification(id, true, new BikeId("BIKE-ALT"));
        awaitWaitingAt(instance, FlowNodes.EventHandoverReported.INSTANCE);

        process.correlateApplicationWithdrawn(id);
        awaitEnded(instance);

        assertThat(instance).isEnded().hasPassed(FlowNodes.EndEventApplicationCancelled.ELEMENT_ID);
        long orderCancellations = engine.getHistoryService()
                .createHistoricProcessInstanceQuery()
                .processDefinitionKey(CancelBikeOrderProcessApi.PROCESS_ID.getValue())
                .superProcessInstanceId(instance.getId())
                .count();
        Assertions.assertThat(orderCancellations).isEqualTo(1);
        verify(requestOrderCancellationUseCase).requestCancellation(new OrderId("ORDER-2"));
        verify(bookCancellationCostsUseCase, never()).bookCosts(any());
    }

    private ApplicationId submitUntilHandover() {
        ApplicationId id = submit(35, 3500.0);
        ProcessInstance instance = instanceOf(id);
        awaitWaitingAt(instance, FlowNodes.GatewayAwaitSignature.INSTANCE);
        process.correlateContractSigned(id);
        awaitWaitingAt(instance, FlowNodes.EventHandoverReported.INSTANCE);
        return id;
    }

    /** Drives a signed, insured application to the clarify-alternative task: the dealer has no bike. */
    private ApplicationId submitUntilBikeUnavailable() {
        when(orderBikeUseCase.orderBike(any(), any()))
                .thenThrow(new BikeUnavailableException(REQUESTED_BIKE))
                .thenReturn(new OrderId("ORDER-2"));

        ApplicationId id = submit(35, 3500.0);
        awaitWaitingAt(instanceOf(id), FlowNodes.GatewayAwaitSignature.INSTANCE);
        process.correlateContractSigned(id);
        awaitUserTask(engine, FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID);
        return id;
    }
}
