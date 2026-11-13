package io.miragon.blueprint.adapter.process

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.bike.BikeUnavailableException
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.bpmn.runtime.path.ProcessPath
import io.miragon.bpmn.runtime.path.enter
import io.miragon.bpmn.runtime.path.inside
import io.miragon.bpmn.runtime.path.interruptedBy
import io.miragon.bpmn.runtime.path.onto
import io.miragon.bpmn.runtime.path.then
import io.miragon.bpmn.runtime.path.throwingCompensation
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions
import org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource

/**
 * Integration process test of the `bike-leasing` model, running as in production: the job executor
 * runs the async continuations and the external-task client polls `/engine-rest` with every worker
 * subscribed. The test only acts where a person or a partner would — messages and user tasks through
 * the real process adapter, timers fired early — and waits for the process to reach the next wait state.
 */
@TestPropertySource(
    properties = [
        "camunda.bpm.job-execution.enabled=true",
        "camunda.bpm.job-execution.wait-time-in-millis=50",
        "camunda.bpm.job-execution.max-wait=200",
    ],
)
class BikeLeasingPollingIntegrationProcessTest : InMemoryEngineIntegrationTest() {

    @Test
    fun `happy path - contract signed, bike available, leasing becomes active`() {
        val id = submit(age = 35, income = 3500.0)
        val instance = instanceOf(id)
        awaitWaitingAt(instance, FlowNodes.GatewayAwaitSignature)

        process.correlateContractSigned(id)
        awaitWaitingAt(instance, FlowNodes.EventHandoverReported)

        process.correlateHandoverReported(id)
        awaitWaitingAt(instance, FlowNodes.EventWithdrawalPeriodElapsed)
        engine.fireTimerAndContinueInBackground(FlowNodes.EventWithdrawalPeriodElapsed)
        awaitEnded(instance)

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
        val orderIdVariable = FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.value
        assertThat(instance).variables().containsEntry(orderIdVariable, "ORDER-1")

        verify(exactly = 1) { sendContractUseCase.sendContract(id) }
        verify(exactly = 1) { issueInsurancePolicyUseCase.issuePolicy(id) }
        verify(exactly = 1) { orderBikeUseCase.orderBike(id, REQUESTED_BIKE) }
        verify(exactly = 1) { activateLeasingUseCase.activate(id) }
    }

    @Test
    fun `escalation - contract not signed in time is escalated and rejected`() {
        val id = submit(age = 35, income = 3500.0)
        val instance = instanceOf(id)
        awaitWaitingAt(instance, FlowNodes.GatewayAwaitSignature)

        engine.fireTimerAndContinueInBackground(FlowNodes.EventSignatureDeadline)
        awaitEnded(instance)

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

        verify(exactly = 1) { rejectApplicationUseCase.reject(id) }
    }

    @Test
    fun `not solvent - the DMN routes the application straight to rejection`() {
        val id = submit(age = 15, income = 3500.0)
        val instance = instanceOf(id)
        awaitEnded(instance)

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
                FlowNodes.SubProcessConcludeContract.ELEMENT_ID,
                FlowNodes.EndEventLeasingActive.ELEMENT_ID,
            )

        verify(exactly = 1) { rejectApplicationUseCase.reject(id) }
        verify(exactly = 0) { sendContractUseCase.sendContract(any()) }
    }

    @Test
    fun `reminder - an unsigned contract gets one reminder before the deadline rejects it`() {
        val id = submit(age = 35, income = 3500.0)
        val instance = instanceOf(id)
        awaitWaitingAt(instance, FlowNodes.GatewayAwaitSignature)

        engine.fireTimerAndContinueInBackground(FlowNodes.EventSignatureReminder)
        awaitPassed(instance, FlowNodes.EndEventProspectReminded)
        assertThat(instance).isWaitingAt(FlowNodes.GatewayAwaitSignature.ELEMENT_ID)

        engine.fireTimerAndContinueInBackground(FlowNodes.EventSignatureDeadline)
        awaitEnded(instance)

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

        verify(exactly = 1) { sendSignatureReminderUseCase.sendSignatureReminder(id) }
        verify(exactly = 1) { rejectApplicationUseCase.reject(id) }
    }

    @Test
    fun `abort - withdrawing the application compensates the completed steps`() {
        every { requestOrderCancellationUseCase.requestCancellation(any()) } returns false

        val id = submitUntilHandover()
        val instance = instanceOf(id)

        process.correlateApplicationWithdrawn(id)
        engine.awaitUserTask(CancelBikeOrderProcessApi.FlowNodes.UserTaskClarifyReturn.ELEMENT_ID)
        process.completeReturnClarification(id)
        awaitEnded(instance)

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

        verify(exactly = 1) { cancelContractUseCase.cancelContract(id) }
        verify(exactly = 1) { cancelInsurancePolicyUseCase.cancelPolicy(id) }
        verify(exactly = 1) { requestOrderCancellationUseCase.requestCancellation(OrderId("ORDER-1")) }
        verify(exactly = 1) { bookCancellationCostsUseCase.bookCosts(OrderId("ORDER-1")) }
        verify(exactly = 1) { sendCancellationConfirmationUseCase.sendCancellationConfirmation(id) }
    }

    @Test
    fun `bike unavailable - clarifying an alternative re-orders and leasing becomes active`() {
        val id = submitUntilBikeUnavailable()
        val instance = instanceOf(id)

        process.completeAlternativeClarification(id, alternativeFound = true, bikeId = BikeId("BIKE-ALT"))
        awaitWaitingAt(instance, FlowNodes.EventHandoverReported)

        process.correlateHandoverReported(id)
        awaitWaitingAt(instance, FlowNodes.EventWithdrawalPeriodElapsed)
        engine.fireTimerAndContinueInBackground(FlowNodes.EventWithdrawalPeriodElapsed)
        awaitEnded(instance)

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

        verify(exactly = 1) { orderBikeUseCase.orderBike(id, REQUESTED_BIKE) }
        verify(exactly = 1) { orderBikeUseCase.orderBike(id, BikeId("BIKE-ALT")) }
        verify(exactly = 1) { activateLeasingUseCase.activate(id) }
    }

    @Test
    fun `bike unavailable - declining the alternative reverses contract and policy without cancelling an order`() {
        val id = submitUntilBikeUnavailable()
        val instance = instanceOf(id)

        process.completeAlternativeClarification(id, alternativeFound = false, bikeId = null)
        awaitEnded(instance)

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

        verify(exactly = 1) { cancelContractUseCase.cancelContract(id) }
        verify(exactly = 1) { cancelInsurancePolicyUseCase.cancelPolicy(id) }
        verify(exactly = 0) { requestOrderCancellationUseCase.requestCancellation(any()) }
    }

    @Test
    fun `abort while clarifying an alternative - compensates contract and policy without cancelling an order`() {
        val id = submitUntilBikeUnavailable()
        val instance = instanceOf(id)

        process.correlateApplicationWithdrawn(id)
        awaitEnded(instance)

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

        verify(exactly = 1) { sendCancellationConfirmationUseCase.sendCancellationConfirmation(id) }
        verify(exactly = 0) { requestOrderCancellationUseCase.requestCancellation(any()) }
    }

    @Test
    fun `abort after an accepted alternative - cancels the one placed order exactly once`() {
        every { requestOrderCancellationUseCase.requestCancellation(any()) } returns true

        val id = submitUntilBikeUnavailable()
        val instance = instanceOf(id)

        process.completeAlternativeClarification(id, alternativeFound = true, bikeId = BikeId("BIKE-ALT"))
        awaitWaitingAt(instance, FlowNodes.EventHandoverReported)

        process.correlateApplicationWithdrawn(id)
        awaitEnded(instance)

        assertThat(instance).isEnded.hasPassed(FlowNodes.EndEventApplicationCancelled.ELEMENT_ID)
        val orderCancellations = engine.historyService.createHistoricProcessInstanceQuery()
            .processDefinitionKey(CancelBikeOrderProcessApi.PROCESS_ID.value)
            .superProcessInstanceId(instance.id)
            .count()
        Assertions.assertThat(orderCancellations).isEqualTo(1)
        verify(exactly = 1) { requestOrderCancellationUseCase.requestCancellation(OrderId("ORDER-2")) }
        verify(exactly = 0) { bookCancellationCostsUseCase.bookCosts(any()) }
    }

    private fun submitUntilHandover(): ApplicationId {
        val id = submit(age = 35, income = 3500.0)
        val instance = instanceOf(id)
        awaitWaitingAt(instance, FlowNodes.GatewayAwaitSignature)
        process.correlateContractSigned(id)
        awaitWaitingAt(instance, FlowNodes.EventHandoverReported)
        return id
    }

    /** Drives a signed, insured application to the clarify-alternative task: the dealer has no bike. */
    private fun submitUntilBikeUnavailable(): ApplicationId {
        every { orderBikeUseCase.orderBike(any(), any()) } throws
            BikeUnavailableException(REQUESTED_BIKE) andThen OrderId("ORDER-2")

        val id = submit(age = 35, income = 3500.0)
        awaitWaitingAt(instanceOf(id), FlowNodes.GatewayAwaitSignature)
        process.correlateContractSigned(id)
        engine.awaitUserTask(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID)
        return id
    }
}
