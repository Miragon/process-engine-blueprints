package io.miragon.blueprint.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import io.miragon.blueprint.adapter.outbound.db.LeasingApplicationJpaRepository;
import io.miragon.blueprint.adapter.outbound.workflow.LeasingProcessAdapter;
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi;
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes;
import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi;
import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
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
import io.miragon.blueprint.application.port.inbound.ValidateApplicationUseCase;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.ApplicationInvalidException;
import io.miragon.blueprint.domain.leasing.ContractId;
import io.miragon.blueprint.domain.leasing.CustomerName;
import io.miragon.blueprint.domain.leasing.Email;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import io.miragon.bpmn.runtime.path.PathWalk;
import io.miragon.common.test.config.WorkflowDeploymentTestConfiguration;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Process test for the MiraVelo Bike-Leasing process on a real (in-container) Camunda 8 engine.
 * {@code @CamundaSpringProcessTest} starts the engine, VanillaBP deploys the models and binds the
 * {@code @WorkflowTask} methods, and the use cases are mocked so we assert the <em>process</em>
 * behaviour together with what VanillaBP persists on the workflow aggregate. Because Zeebe drives
 * itself asynchronously, {@code CamundaAssert} (which polls until a timeout) doubles as the
 * synchronisation mechanism: asserting a wait-state element has completed is how we wait for it.
 * Timers are advanced with {@code processTestContext.increaseTime} and messages and the clarification
 * user task go through the real process adapter, inside a transaction like the application services
 * provide.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@CamundaSpringProcessTest
@ActiveProfiles("test")
@Import(WorkflowDeploymentTestConfiguration.class)
class BikeLeasingProcessTest {

    @Autowired
    private CamundaClient camundaClient;

    @Autowired
    private CamundaProcessTestContext processTestContext;

    @Autowired
    private LeasingProcessAdapter process;

    @Autowired
    private LeasingApplicationJpaRepository aggregates;

    @Autowired
    private TransactionTemplate transaction;

    @MockitoBean
    private ValidateApplicationUseCase validateApplicationUseCase;

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
        when(sendContractUseCase.sendContract(any()))
                .thenAnswer(call -> application(call.getArgument(0)).withContract(new ContractId("CONTRACT-1")));
        when(orderBikeUseCase.orderBike(any()))
                .thenAnswer(call -> application(call.getArgument(0)).documentOrder(new OrderId("ORDER-1")));
        when(activateLeasingUseCase.activate(any()))
                .thenAnswer(call -> application(call.getArgument(0)).activate());
        when(rejectApplicationUseCase.reject(any()))
                .thenAnswer(call -> application(call.getArgument(0)).reject());
        when(sendCancellationConfirmationUseCase.sendCancellationConfirmation(any()))
                .thenAnswer(call -> application(call.getArgument(0)).cancel());
    }

    @Test
    void happyPathContractSignedBikeAvailableLeasingBecomesActive() {
        ApplicationId id = submit(35, 3500.0);
        long instanceKey = awaitProcessInstance();
        ProcessInstanceSelector instance = ProcessInstanceSelectors.byKey(instanceKey);

        // wait until the contract was sent and the process parks on the await-signature gateway
        CamundaAssert.assertThatProcessInstance(instance)
                .hasCompletedElements(FlowNodes.ServiceTaskSendContract.ELEMENT_ID);
        assertThat(aggregateOf(id).getContractId()).isEqualTo("CONTRACT-1");
        inTransaction(() -> process.correlateContractSigned(id));

        // the fork runs insurance + bike order, then joins and parks on the handover message
        CamundaAssert.assertThatProcessInstance(instance)
                .hasCompletedElements(
                        FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID,
                        FlowNodes.ServiceTaskOrderBike.ELEMENT_ID);
        inTransaction(() -> process.correlateHandoverReported(id));

        // the 14-day withdrawal period elapses
        CamundaAssert.assertThatProcessInstance(instance)
                .hasCompletedElements(FlowNodes.EventHandoverReported.ELEMENT_ID);
        processTestContext.increaseTime(Duration.ofDays(14).plusHours(1));

        CamundaAssert.assertThatProcessInstance(instance).isCompleted();
        CamundaAssert.assertThatProcessInstance(instance)
                .hasCompletedElementsInOrder(
                        pathUntilContractSigned()
                                .then(next -> next.gatewayFork())
                                .then(next -> next.serviceTaskIssueInsurancePolicy())
                                .then(next -> next.gatewayJoin())
                                .then(next -> next.eventHandoverReported())
                                .then(next -> next.eventWithdrawalPeriodElapsed())
                                .then(next -> next.serviceTaskActivateLeasing())
                                .end(next -> next.endEventLeasingActive())
                                .getIds())
                .hasCompletedElementsInOrder(
                        PathWalk.from(FlowNodes.gatewayFork())
                                .then(next -> next.gatewayBikeSourceJoin())
                                .then(next -> next.serviceTaskOrderBike())
                                .then(next -> next.gatewayBikeAvailable())
                                .then(next -> next.gatewayJoin())
                                .getIds());
        verify(sendContractUseCase, times(1)).sendContract(argThat(application -> application.id().equals(id)));
        verify(issueInsurancePolicyUseCase, times(1)).issuePolicy(id);
        verify(activateLeasingUseCase, times(1)).activate(argThat(application -> application.id().equals(id)));

        // the workflow aggregate, reloaded from the database, carries everything the tasks changed
        LeasingApplicationAggregate aggregate = aggregateOf(id);
        assertThat(aggregate.getStatus()).isEqualTo(LeasingStatus.ACTIVE);
        assertThat(aggregate.getOrderId()).isEqualTo("ORDER-1");
        assertThat(aggregate.getContractId()).isEqualTo("CONTRACT-1");
    }

    @Test
    void theEngineOnlyEverSeesTheRoutingValuesTheWorkflowAggregateReleases() {
        ApplicationId id = submit(35, 3500.0);
        long instanceKey = awaitProcessInstance();
        ProcessInstanceSelector instance = ProcessInstanceSelectors.byKey(instanceKey);

        CamundaAssert.assertThatProcessInstance(instance)
                .hasCompletedElements(FlowNodes.ServiceTaskSendContract.ELEMENT_ID);
        inTransaction(() -> process.correlateContractSigned(id));
        CamundaAssert.assertThatProcessInstance(instance)
                .hasCompletedElements(
                        FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID,
                        FlowNodes.ServiceTaskOrderBike.ELEMENT_ID);

        List<Map.Entry<String, String>> variables = awaitVariable(instanceKey, "bikeAvailable", "true");
        assertThat(variables)
                .extracting(Map.Entry::getKey)
                .containsOnly("applicationId", "age", "monthlyNetIncome", "solvent", "bikeAvailable");
        assertThat(variables).contains(Map.entry("applicationId", "\"" + id.value() + "\""));
    }

    @Test
    void escalationContractNotSignedInTimeIsEscalatedAndRejected() {
        ApplicationId id = submit(35, 3500.0);
        long instanceKey = awaitProcessInstance();
        ProcessInstanceSelector instance = ProcessInstanceSelectors.byKey(instanceKey);

        // park on the await-signature gateway, then let the 14-day signature deadline elapse
        CamundaAssert.assertThatProcessInstance(instance)
                .hasCompletedElements(FlowNodes.ServiceTaskSendContract.ELEMENT_ID);
        processTestContext.increaseTime(Duration.ofDays(14).plusHours(1));

        CamundaAssert.assertThatProcessInstance(instance).isCompleted();
        // Zeebe terminates the throwing end event and its sub-process, so the walk resumes at the boundary event
        CamundaAssert.assertThatProcessInstance(instance)
                .hasCompletedElementsInOrder(
                        pathUntilSignatureAwaited().then(next -> next.eventSignatureDeadline()).getIds())
                .hasTerminatedElements(
                        FlowNodes.EndEventNotSigned.ELEMENT_ID,
                        FlowNodes.SubProcessConcludeContract.ELEMENT_ID)
                .hasCompletedElementsInOrder(
                        PathWalk.from(FlowNodes.eventContractNotSigned())
                                .then(next -> next.gatewayRejectionJoin())
                                .then(next -> next.serviceTaskSendRejection())
                                .end(next -> next.endEventApplicationRejected())
                                .getIds());
        verify(rejectApplicationUseCase, times(1)).reject(argThat(application -> application.id().equals(id)));
        assertThat(aggregateOf(id).getStatus()).isEqualTo(LeasingStatus.REJECTED);
    }

    @Test
    void notSolventTheDmnRoutesTheApplicationStraightToRejection() {
        // age below 18 cannot sign a leasing contract, so the DMN returns solvent = false
        ApplicationId id = submit(15, 3500.0);
        long instanceKey = awaitProcessInstance();
        ProcessInstanceSelector instance = ProcessInstanceSelectors.byKey(instanceKey);

        CamundaAssert.assertThatProcessInstance(instance).isCompleted();
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElementsInOrder(
                pathUntilCreditRatingChecked()
                        .then(next -> next.gatewayRejectionJoin())
                        .then(next -> next.serviceTaskSendRejection())
                        .end(next -> next.endEventApplicationRejected())
                        .getIds());
        verify(rejectApplicationUseCase, times(1)).reject(argThat(application -> application.id().equals(id)));
        verify(sendContractUseCase, never()).sendContract(any());
    }

    @Test
    void invalidApplicationTheBpmnErrorRoutesTheApplicationToRejection() {
        doAnswer(call -> {
            throw new ApplicationInvalidException(application(call.getArgument(0)).id(), "no income");
        }).when(validateApplicationUseCase).validate(any());

        ApplicationId id = submit(35, 0.0);
        long instanceKey = awaitProcessInstance();
        ProcessInstanceSelector instance = ProcessInstanceSelectors.byKey(instanceKey);

        CamundaAssert.assertThatProcessInstance(instance).isCompleted();
        // Zeebe terminates the task that threw the BPMN error, so the walk starts at the boundary event
        CamundaAssert.assertThatProcessInstance(instance)
                .hasTerminatedElements(FlowNodes.ServiceTaskValidateApplication.ELEMENT_ID)
                .hasCompletedElementsInOrder(
                        PathWalk.from(FlowNodes.eventApplicationInvalid())
                                .then(next -> next.gatewayRejectionJoin())
                                .then(next -> next.serviceTaskSendRejection())
                                .end(next -> next.endEventApplicationRejected())
                                .getIds());
        verify(rejectApplicationUseCase, times(1)).reject(argThat(application -> application.id().equals(id)));
        verify(sendContractUseCase, never()).sendContract(any());
        assertThat(aggregateOf(id).getStatus()).isEqualTo(LeasingStatus.REJECTED);
    }

    @Test
    void abortWithdrawingTheApplicationCompensatesTheCompletedSteps() {
        when(requestOrderCancellationUseCase.requestCancellation(any())).thenReturn(true);

        ApplicationId id = submit(35, 3500.0);
        long instanceKey = awaitProcessInstance();
        ProcessInstanceSelector instance = ProcessInstanceSelectors.byKey(instanceKey);

        // drive to the handover wait state (contract signed, bike ordered, insured)
        CamundaAssert.assertThatProcessInstance(instance)
                .hasCompletedElements(FlowNodes.ServiceTaskSendContract.ELEMENT_ID);
        inTransaction(() -> process.correlateContractSigned(id));
        CamundaAssert.assertThatProcessInstance(instance)
                .hasCompletedElements(
                        FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID,
                        FlowNodes.ServiceTaskOrderBike.ELEMENT_ID);

        // withdraw -> compensation. The cancelBikeOrder call activity runs in its own (child)
        // process instance and parks on its clarify-return task, so we look it up by element id only.
        inTransaction(() -> process.correlateApplicationWithdrawn(id));
        awaitUserTaskCreated(CancelBikeOrderProcessApi.FlowNodes.UserTaskClarifyReturn.ELEMENT_ID, null);
        processTestContext.completeUserTask(CancelBikeOrderProcessApi.FlowNodes.UserTaskClarifyReturn.ELEMENT_ID);

        CamundaAssert.assertThatProcessInstance(instance).isCompleted();
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(
                PathWalk.from(FlowNodes.startEventApplicationWithdrawn())
                        .then(next -> next.eventReverseApplication())
                        .throwingCompensation(
                                FlowNodes.eventCompensateContract(), next -> next.serviceTaskCancelContract())
                        .throwingCompensation(
                                FlowNodes.eventCompensateInsurance(), next -> next.serviceTaskCancelPolicy())
                        .throwingCompensation(
                                FlowNodes.eventCompensateOrder(), next -> next.callActivityCancelBikeOrder())
                        .then(next -> next.serviceTaskSendCancellationConfirmation())
                        .end(next -> next.endEventApplicationCancelled())
                        .getDistinctIds());
        verify(cancelContractUseCase, times(1)).cancelContract(argThat(application -> application.id().equals(id)));
        verify(cancelInsurancePolicyUseCase, times(1)).cancelPolicy(id);
        verify(requestOrderCancellationUseCase, times(1)).requestCancellation(new OrderId("ORDER-1"));
        verify(bookCancellationCostsUseCase, times(1)).bookCosts(new OrderId("ORDER-1"));
        verify(sendCancellationConfirmationUseCase, times(1))
                .sendCancellationConfirmation(argThat(application -> application.id().equals(id)));

        // the tasks of the called process worked on the very same workflow aggregate
        LeasingApplicationAggregate aggregate = aggregateOf(id);
        assertThat(aggregate.getStatus()).isEqualTo(LeasingStatus.CANCELLED);
        assertThat(aggregate.getCancellationPossible()).isTrue();
    }

    @Test
    void bikeUnavailableClarifyingAnAlternativeReOrdersAndLeasingBecomesActive() {
        // the first order finds the requested bike unavailable; the re-order after the alternative succeeds
        doAnswer(call -> application(call.getArgument(0)))
                .doAnswer(call -> application(call.getArgument(0)).documentOrder(new OrderId("ORDER-2")))
                .when(orderBikeUseCase).orderBike(any());

        ApplicationId id = submit(35, 3500.0);
        long instanceKey = awaitProcessInstance();
        ProcessInstanceSelector instance = ProcessInstanceSelectors.byKey(instanceKey);

        CamundaAssert.assertThatProcessInstance(instance)
                .hasCompletedElements(FlowNodes.ServiceTaskSendContract.ELEMENT_ID);
        inTransaction(() -> process.correlateContractSigned(id));

        // the first order finds nothing -> parks on the clarify-alternative user task, which
        // VanillaBP reports to the workflow aggregate
        awaitUserTaskCreated(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID, instanceKey);
        LeasingApplicationAggregate parked = aggregateOf(id);
        assertThat(parked.getClarifyAlternativeTaskId()).isNotNull();
        assertThat(parked.getClarificationRequestedAt()).isNotNull();
        inTransaction(() -> process.completeAlternativeClarification(id, true));

        // re-order succeeds -> join -> handover -> withdrawal period
        CamundaAssert.assertThatProcessInstance(instance)
                .hasCompletedElements(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID);
        inTransaction(() -> process.correlateHandoverReported(id));
        CamundaAssert.assertThatProcessInstance(instance)
                .hasCompletedElements(FlowNodes.EventHandoverReported.ELEMENT_ID);
        processTestContext.increaseTime(Duration.ofDays(14).plusHours(1));

        CamundaAssert.assertThatProcessInstance(instance).isCompleted();
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElementsInOrder(
                PathWalk.from(FlowNodes.gatewayFork())
                        .then(next -> next.gatewayBikeSourceJoin())
                        .then(next -> next.serviceTaskOrderBike())
                        .then(next -> next.gatewayBikeAvailable())
                        .then(next -> next.userTaskClarifyAlternative())
                        .then(next -> next.gatewayAlternativeFound())
                        .then(next -> next.gatewayBikeSourceJoin())
                        .then(next -> next.serviceTaskOrderBike())
                        .then(next -> next.gatewayBikeAvailable())
                        .then(next -> next.gatewayJoin())
                        .then(next -> next.eventHandoverReported())
                        .then(next -> next.eventWithdrawalPeriodElapsed())
                        .then(next -> next.serviceTaskActivateLeasing())
                        .end(next -> next.endEventLeasingActive())
                        .getIds());
        verify(orderBikeUseCase, times(2)).orderBike(argThat(application -> application.id().equals(id)));

        // the clarification is closed on the reloaded workflow aggregate and the re-order is recorded
        LeasingApplicationAggregate aggregate = aggregateOf(id);
        assertThat(aggregate.getClarifyAlternativeTaskId()).isNull();
        assertThat(aggregate.getAlternativeFound()).isTrue();
        assertThat(aggregate.getOrderId()).isEqualTo("ORDER-2");
        assertThat(aggregate.getStatus()).isEqualTo(LeasingStatus.ACTIVE);
    }

    @Test
    void dealerOutageTheOrderRetriesCountDownAndTheEngineRaisesAnIncident() {
        doThrow(new IllegalStateException("Bike dealer API unavailable")).when(orderBikeUseCase).orderBike(any());

        ApplicationId id = submit(35, 3500.0);
        long instanceKey = awaitProcessInstance();
        ProcessInstanceSelector instance = ProcessInstanceSelectors.byKey(instanceKey);

        CamundaAssert.assertThatProcessInstance(instance)
                .hasCompletedElements(FlowNodes.ServiceTaskSendContract.ELEMENT_ID);
        inTransaction(() -> process.correlateContractSigned(id));

        // each failed attempt costs one of the three retries modelled on the task; the engine clock is
        // pushed past the modelled 10s backoff so the countdown does not slow the test down
        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    processTestContext.increaseTime(Duration.ofSeconds(10));
                    assertThat(camundaClient.newIncidentSearchRequest()
                            .filter(filter -> filter.processInstanceKey(instanceKey))
                            .send()
                            .join()
                            .items()).isNotEmpty();
                });

        verify(orderBikeUseCase, times(3)).orderBike(argThat(application -> application.id().equals(id)));
        CamundaAssert.assertThatProcessInstance(instance).isActive();
        assertThat(aggregateOf(id).getOrderId()).isNull();
    }

    private PathWalk<FlowNodes.GatewayIsSolvent, FlowNodes.GatewayIsSolvent.Next> pathUntilCreditRatingChecked() {
        return PathWalk.from(FlowNodes.startEventLeasingRequestReceived())
                .then(next -> next.serviceTaskValidateApplication())
                .then(next -> next.businessRuleTaskCheckCreditRating())
                .then(next -> next.gatewayIsSolvent());
    }

    private PathWalk<FlowNodes.GatewayAwaitSignature, FlowNodes.GatewayAwaitSignature.Next> pathUntilSignatureAwaited() {
        return pathUntilCreditRatingChecked()
                .onto(next -> next.subProcessConcludeContract())
                .enter(FlowNodes.subProcessConcludeContract(), start -> start.startEventCustomerEligible())
                .then(next -> next.serviceTaskSendContract())
                .then(next -> next.gatewayAwaitSignature());
    }

    private PathWalk<FlowNodes.SubProcessConcludeContract, FlowNodes.SubProcessConcludeContract.Next> pathUntilContractSigned() {
        return pathUntilCreditRatingChecked()
                .onto(next -> next.subProcessConcludeContract())
                .inside(
                        FlowNodes.subProcessConcludeContract(),
                        start -> PathWalk.from(start.startEventCustomerEligible())
                                .then(next -> next.serviceTaskSendContract())
                                .then(next -> next.gatewayAwaitSignature())
                                .then(next -> next.eventContractSigned())
                                .end(next -> next.endEventContractValid()));
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
        inTransaction(() -> process.submitRequest(application));
        return application.id();
    }

    private void inTransaction(Runnable action) {
        transaction.executeWithoutResult(status -> action.run());
    }

    private LeasingApplicationAggregate aggregateOf(ApplicationId id) {
        return aggregates.findById(id.value().toString()).orElseThrow();
    }

    private static LeasingApplication application(Object argument) {
        return (LeasingApplication) argument;
    }

    private List<Map.Entry<String, String>> awaitVariable(long processInstanceKey, String name, String value) {
        AtomicReference<List<Map.Entry<String, String>>> variables = new AtomicReference<>(List.of());
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    variables.set(camundaClient.newVariableSearchRequest()
                            .filter(filter -> filter.processInstanceKey(processInstanceKey))
                            .send()
                            .join()
                            .items()
                            .stream()
                            .map(variable -> Map.entry(variable.getName(), variable.getValue()))
                            .toList());
                    assertThat(variables.get()).contains(Map.entry(name, value));
                });
        return variables.get();
    }

    private long awaitProcessInstance() {
        AtomicLong instanceKey = new AtomicLong();
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    var instances = camundaClient.newProcessInstanceSearchRequest()
                            .filter(filter -> filter.processDefinitionId(BikeLeasingProcessProcessApi.PROCESS_ID.getValue()))
                            .send()
                            .join()
                            .items();
                    assertThat(instances).as("no bike-leasing process instance was started").isNotEmpty();
                    instanceKey.set(instances.getFirst().getProcessInstanceKey());
                });
        return instanceKey.get();
    }

    private void awaitUserTaskCreated(String elementId, Long processInstanceKey) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    var tasks = camundaClient.newUserTaskSearchRequest()
                            .filter(filter -> {
                                filter.elementId(elementId);
                                filter.state(UserTaskState.CREATED);
                                if (processInstanceKey != null) {
                                    filter.processInstanceKey(processInstanceKey);
                                }
                            })
                            .send()
                            .join()
                            .items();
                    assertThat(tasks).as("user task '" + elementId + "' was not created").isNotEmpty();
                });
    }
}
