package io.miragon.blueprint.process;

import static io.miragon.blueprint.process.ProcessDriveUtils.completeExternalTask;
import static io.miragon.blueprint.process.ProcessDriveUtils.completeUserTask;
import static io.miragon.blueprint.process.ProcessDriveUtils.correlateMessage;
import static io.miragon.blueprint.process.ProcessDriveUtils.drainToWaitState;
import static io.miragon.blueprint.process.ProcessDriveUtils.findInstance;
import static io.miragon.blueprint.process.ProcessDriveUtils.fireTimer;
import static io.miragon.blueprint.process.ProcessDriveUtils.startLeasing;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.init;

import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.FlowNodes;
import io.miragon.bpmn.runtime.path.PathWalk;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.delegate.ExecutionListener;
import org.operaton.bpm.engine.delegate.TaskListener;
import org.operaton.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.operaton.bpm.engine.impl.mock.MockExpressionManager;
import org.operaton.bpm.engine.impl.mock.Mocks;
import org.operaton.bpm.engine.runtime.ProcessInstance;

/**
 * Process-model behaviour test for the {@code bike-leasing} model — it lives with the
 * <strong>process owner</strong> (this service), which also owns and deploys the model. Because the
 * worker embeds no engine, the test spins up a fresh <strong>standalone in-memory engine</strong> per
 * test and deploys the model from this service's own resources.
 *
 * <p>The service tasks are <strong>external tasks</strong>, so the test completes each one explicitly by
 * topic via {@link ProcessDriveUtils#completeExternalTask} (supplying the output variables a real worker
 * would return); user tasks, messages and timers are released explicitly. It therefore verifies the
 * <em>topology</em> — routing, gateways, compensation, the event sub-process, the DMN and the timers —
 * independent of what the workers actually do with each task.
 */
class BikeLeasingProcessTest {

    private ProcessEngine engine;

    /** Output of the {@code orderBike} external task when the requested bike is available. */
    private final Map<String, Object> bikeAvailable = Map.of(
            FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.getValue(), "ORDER-1",
            FlowNodes.ServiceTaskOrderBike.Variables.BIKE_AVAILABLE.getValue(), true);

    @BeforeEach
    void setUp() {
        // The job executor stays off by default, so the test drives async continuations by hand.
        // The model references two listeners by expression (`#{bikeOrderAuditListener}`,
        // `#{clarifyAlternativeTaskListener}`) whose real beans live in the `engine-service` — listeners
        // run inside the engine and have no external-task equivalent, so they cannot be worker beans.
        // The standalone test engine has no Spring context, so a `MockExpressionManager` + `Mocks`
        // registers no-op stand-ins under those bean names, letting the topology test resolve (and thus
        // verify) the `delegateExpression`s without pulling in the engine module.
        StandaloneInMemProcessEngineConfiguration configuration = new StandaloneInMemProcessEngineConfiguration();
        configuration.setJdbcUrl("jdbc:h2:mem:bikeleasing-process-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=1000");
        configuration.setExpressionManager(new MockExpressionManager());
        engine = configuration.buildProcessEngine();

        Mocks.register("bikeOrderAuditListener", (ExecutionListener) execution -> {
            /* stand-in: real bean in engine-service */
        });
        Mocks.register("clarifyAlternativeTaskListener", (TaskListener) task -> {
            /* stand-in: real bean in engine-service */
        });

        // The process owner deploys its own model — here from this service's own resources on the classpath.
        engine.getRepositoryService().createDeployment()
                .addClasspathResource("bpmn/bike-leasing.bpmn")
                .addClasspathResource("bpmn/cancel-bike-order.bpmn")
                .addClasspathResource("dmn/check-credit-rating.dmn")
                .deploy();

        init(engine);
    }

    @AfterEach
    void tearDown() {
        Mocks.reset();
        engine.close();
    }

    @Test
    void happyPathContractSignedBikeAvailableLeasingBecomesActive() {
        String businessKey = submit(35, 3500.0);
        ProcessInstance instance = findInstance(engine, businessKey);

        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_VALIDATE_APPLICATION); // -> DMN (solvent) -> sendContract
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_SEND_CONTRACT); // -> signature wait state

        correlateMessage(engine, Messages.MIRAVELO_CONTRACT_SIGNED, businessKey); // forks into insurance + bike order
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_ISSUE_INSURANCE_POLICY);
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_ORDER_BIKE, bikeAvailable); // joins -> handover wait state

        correlateMessage(engine, Messages.MIRAVELO_HANDOVER_REPORTED, businessKey); // -> withdrawal-period timer
        fireTimer(engine, FlowNodes.EventWithdrawalPeriodElapsed.INSTANCE);
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_ACTIVATE_LEASING); // flips read model to ACTIVE -> end

        assertThat(instance)
                .isEnded()
                .hasPassedInOrder(
                        pathUntilContractSigned()
                                .then(next -> next.gatewayFork())
                                .then(next -> next.serviceTaskIssueInsurancePolicy())
                                .then(next -> next.gatewayJoin())
                                .then(next -> next.eventHandoverReported())
                                .then(next -> next.eventWithdrawalPeriodElapsed())
                                .then(next -> next.serviceTaskActivateLeasing())
                                .end(next -> next.endEventLeasingActive())
                                .getIds())
                .hasPassedInOrder(
                        PathWalk.from(FlowNodes.GatewayFork.INSTANCE)
                                .then(next -> next.gatewayBikeSourceJoin())
                                .then(next -> next.serviceTaskOrderBike())
                                .then(next -> next.gatewayBikeAvailable())
                                .then(next -> next.gatewayJoin())
                                .getIds())
                .hasNotPassed(
                        FlowNodes.EndEventApplicationRejected.ELEMENT_ID,
                        FlowNodes.EndEventApplicationCancelled.ELEMENT_ID,
                        FlowNodes.EndEventContractCancelled.ELEMENT_ID);
    }

    @Test
    void escalationContractNotSignedInTimeIsEscalatedAndRejected() {
        String businessKey = submit(35, 3500.0);
        ProcessInstance instance = findInstance(engine, businessKey);

        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_VALIDATE_APPLICATION);
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_SEND_CONTRACT); // -> signature wait state

        fireTimer(engine, FlowNodes.EventSignatureDeadline.INSTANCE); // deadline -> escalation -> boundary -> rejection
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_SEND_REJECTION); // -> end

        assertThat(instance)
                .isEnded()
                .hasPassedInOrder(
                        pathUntilCreditRatingChecked()
                                .onto(next -> next.subProcessConcludeContract())
                                .inside(FlowNodes.SubProcessConcludeContract.INSTANCE, start ->
                                        pathUntilSignatureAwaited(start)
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
    void notSolventTheDmnRoutesTheApplicationStraightToRejection() {
        // age below 18 cannot sign a leasing contract, so the DMN returns solvent = false
        String businessKey = submit(15, 3500.0);
        ProcessInstance instance = findInstance(engine, businessKey);

        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_VALIDATE_APPLICATION); // -> DMN -> not solvent -> rejection
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_SEND_REJECTION); // -> end

        assertThat(instance)
                .isEnded()
                .hasPassedInOrder(
                        pathUntilCreditRatingChecked()
                                .then(next -> next.gatewayRejectionJoin())
                                .then(next -> next.serviceTaskSendRejection())
                                .end(next -> next.endEventApplicationRejected())
                                .getIds())
                .hasNotPassed(
                        FlowNodes.ServiceTaskSendContract.ELEMENT_ID,
                        FlowNodes.EndEventLeasingActive.ELEMENT_ID);
    }

    @Test
    void abortWithdrawingTheApplicationCompensatesTheCompletedSteps() {
        // Compensation handlers run in an engine-defined order, so drive the chain generically. Only the
        // `requestCancellation` external task produces a variable the flow routes on.
        Map<String, Map<String, Object>> compensationOutputs = Map.of(
                ServiceTasks.BIKE_LEASING_REQUEST_CANCELLATION,
                Map.<String, Object>of(
                        CancelBikeOrderProcessApi.FlowNodes.ServiceTaskRequestCancellation.Variables.CANCELLATION_POSSIBLE.getValue(),
                        true));

        String businessKey = submit(35, 3500.0);
        ProcessInstance instance = findInstance(engine, businessKey);

        // drive to the handover wait state (contract signed, bike ordered, insured)
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_VALIDATE_APPLICATION);
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_SEND_CONTRACT);
        correlateMessage(engine, Messages.MIRAVELO_CONTRACT_SIGNED, businessKey);
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_ISSUE_INSURANCE_POLICY);
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_ORDER_BIKE, bikeAvailable);

        // withdrawing triggers compensation; it parks on the cancelBikeOrder sub-process' user task
        correlateMessage(engine, Messages.MIRAVELO_APPLICATION_WITHDRAWN, businessKey);
        drainToWaitState(engine, compensationOutputs);
        completeUserTask(
                engine,
                CancelBikeOrderProcessApi.FlowNodes.UserTaskClarifyReturn.ELEMENT_ID,
                Map.of("returnClarified", true));
        drainToWaitState(engine, compensationOutputs); // -> sendCancellationConfirmation -> end cancelled

        assertThat(instance)
                .isEnded()
                .hasPassed(
                        PathWalk.from(FlowNodes.StartEventApplicationWithdrawn.INSTANCE)
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
    }

    @Test
    void bikeUnavailableClarifyingAnAlternativeReOrdersAndLeasingBecomesActive() {
        String businessKey = submit(35, 3500.0);
        ProcessInstance instance = findInstance(engine, businessKey);

        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_VALIDATE_APPLICATION);
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_SEND_CONTRACT);
        correlateMessage(engine, Messages.MIRAVELO_CONTRACT_SIGNED, businessKey);
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_ISSUE_INSURANCE_POLICY);

        // the first order finds the requested bike unavailable -> parks on the clarify-alternative task
        Map<String, Object> bikeUnavailable = Map.of(FlowNodes.ServiceTaskOrderBike.Variables.BIKE_AVAILABLE.getValue(), false);
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_ORDER_BIKE, bikeUnavailable);

        completeUserTask(
                engine,
                FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID,
                Map.of(
                        FlowNodes.UserTaskClarifyAlternative.Variables.ALTERNATIVE_FOUND.getValue(), true,
                        FlowNodes.StartEventLeasingRequestReceived.Variables.BIKE_ID.getValue(), "BIKE-ALT"));

        // the re-order succeeds -> parallel join -> handover wait state
        Map<String, Object> reorderAvailable = Map.of(
                FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.getValue(), "ORDER-2",
                FlowNodes.ServiceTaskOrderBike.Variables.BIKE_AVAILABLE.getValue(), true);
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_ORDER_BIKE, reorderAvailable);

        correlateMessage(engine, Messages.MIRAVELO_HANDOVER_REPORTED, businessKey);
        fireTimer(engine, FlowNodes.EventWithdrawalPeriodElapsed.INSTANCE);
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_ACTIVATE_LEASING);

        assertThat(instance)
                .isEnded()
                .hasPassedInOrder(
                        PathWalk.from(FlowNodes.GatewayFork.INSTANCE)
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
                                .getIds())
                .hasNotPassed(
                        FlowNodes.EndEventContractCancelled.ELEMENT_ID,
                        FlowNodes.EndEventApplicationRejected.ELEMENT_ID);
    }

    private PathWalk<FlowNodes.GatewayIsSolvent, FlowNodes.GatewayIsSolvent.Next> pathUntilCreditRatingChecked() {
        return PathWalk.from(FlowNodes.StartEventLeasingRequestReceived.INSTANCE)
                .then(next -> next.serviceTaskValidateApplication())
                .then(next -> next.businessRuleTaskCheckCreditRating())
                .then(next -> next.gatewayIsSolvent());
    }

    private PathWalk<FlowNodes.GatewayAwaitSignature, FlowNodes.GatewayAwaitSignature.Next> pathUntilSignatureAwaited(
            FlowNodes.SubProcessConcludeContract.Start start) {
        return PathWalk.from(start.startEventCustomerEligible())
                .then(next -> next.serviceTaskSendContract())
                .then(next -> next.gatewayAwaitSignature());
    }

    private PathWalk<FlowNodes.SubProcessConcludeContract, FlowNodes.SubProcessConcludeContract.Next>
            pathUntilContractSigned() {
        return pathUntilCreditRatingChecked()
                .onto(next -> next.subProcessConcludeContract())
                .inside(FlowNodes.SubProcessConcludeContract.INSTANCE, start ->
                        pathUntilSignatureAwaited(start)
                                .then(next -> next.eventContractSigned())
                                .end(next -> next.endEventContractValid()));
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
