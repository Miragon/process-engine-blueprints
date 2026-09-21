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

import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.Elements;
import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.Messages;
import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.ServiceTasks;
import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.Variables;
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
            Variables.ServiceTaskOrderBike.ORDER_ID.getValue(), "ORDER-1",
            Variables.ServiceTaskOrderBike.BIKE_AVAILABLE.getValue(), true);

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
        fireTimer(engine, Elements.EVENT_WITHDRAWAL_PERIOD_ELAPSED);
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_ACTIVATE_LEASING); // flips read model to ACTIVE -> end

        assertThat(instance)
                .isEnded()
                .hasPassedInOrder(
                        Elements.SERVICE_TASK_VALIDATE_APPLICATION.getValue(),
                        Elements.BUSINESS_RULE_TASK_CHECK_CREDIT_RATING.getValue(),
                        Elements.SERVICE_TASK_SEND_CONTRACT.getValue(),
                        Elements.SERVICE_TASK_ISSUE_INSURANCE_POLICY.getValue(),
                        Elements.EVENT_HANDOVER_REPORTED.getValue(),
                        Elements.SERVICE_TASK_ACTIVATE_LEASING.getValue(),
                        Elements.END_EVENT_LEASING_ACTIVE.getValue())
                .hasNotPassed(
                        Elements.END_EVENT_APPLICATION_REJECTED.getValue(),
                        Elements.END_EVENT_APPLICATION_CANCELLED.getValue(),
                        Elements.END_EVENT_CONTRACT_CANCELLED.getValue());
    }

    @Test
    void escalationContractNotSignedInTimeIsEscalatedAndRejected() {
        String businessKey = submit(35, 3500.0);
        ProcessInstance instance = findInstance(engine, businessKey);

        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_VALIDATE_APPLICATION);
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_SEND_CONTRACT); // -> signature wait state

        fireTimer(engine, Elements.EVENT_SIGNATURE_DEADLINE); // deadline -> escalation -> boundary -> rejection
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_SEND_REJECTION); // -> end

        assertThat(instance)
                .isEnded()
                .hasPassed(
                        Elements.EVENT_SIGNATURE_DEADLINE.getValue(),
                        Elements.EVENT_CONTRACT_NOT_SIGNED.getValue(),
                        Elements.SERVICE_TASK_SEND_REJECTION.getValue(),
                        Elements.END_EVENT_APPLICATION_REJECTED.getValue())
                .hasNotPassed(Elements.END_EVENT_LEASING_ACTIVE.getValue());
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
                .hasPassed(
                        Elements.SERVICE_TASK_VALIDATE_APPLICATION.getValue(),
                        Elements.BUSINESS_RULE_TASK_CHECK_CREDIT_RATING.getValue(),
                        Elements.SERVICE_TASK_SEND_REJECTION.getValue(),
                        Elements.END_EVENT_APPLICATION_REJECTED.getValue())
                .hasNotPassed(
                        Elements.SERVICE_TASK_SEND_CONTRACT.getValue(),
                        Elements.END_EVENT_LEASING_ACTIVE.getValue());
    }

    @Test
    void abortWithdrawingTheApplicationCompensatesTheCompletedSteps() {
        // Compensation handlers run in an engine-defined order, so drive the chain generically. Only the
        // `requestCancellation` external task produces a variable the flow routes on.
        Map<String, Map<String, Object>> compensationOutputs = Map.of(
                CancelBikeOrderProcessApi.ServiceTasks.BIKE_LEASING_REQUEST_CANCELLATION,
                Map.<String, Object>of(
                        CancelBikeOrderProcessApi.Variables.ServiceTaskRequestCancellation.CANCELLATION_POSSIBLE.getValue(),
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
                CancelBikeOrderProcessApi.Elements.USER_TASK_CLARIFY_RETURN.getValue(),
                Map.of("returnClarified", true));
        drainToWaitState(engine, compensationOutputs); // -> sendCancellationConfirmation -> end cancelled

        assertThat(instance)
                .isEnded()
                .hasPassed(
                        Elements.SERVICE_TASK_CANCEL_CONTRACT.getValue(),
                        Elements.SERVICE_TASK_CANCEL_POLICY.getValue(),
                        Elements.CALL_ACTIVITY_CANCEL_BIKE_ORDER.getValue(),
                        Elements.SERVICE_TASK_SEND_CANCELLATION_CONFIRMATION.getValue(),
                        Elements.END_EVENT_APPLICATION_CANCELLED.getValue())
                .hasNotPassed(Elements.END_EVENT_LEASING_ACTIVE.getValue());
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
        Map<String, Object> bikeUnavailable = Map.of(Variables.ServiceTaskOrderBike.BIKE_AVAILABLE.getValue(), false);
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_ORDER_BIKE, bikeUnavailable);

        completeUserTask(
                engine,
                Elements.USER_TASK_CLARIFY_ALTERNATIVE.getValue(),
                Map.of(
                        Variables.UserTaskClarifyAlternative.ALTERNATIVE_FOUND.getValue(), true,
                        Variables.StartEventLeasingRequestReceived.BIKE_ID.getValue(), "BIKE-ALT"));

        // the re-order succeeds -> parallel join -> handover wait state
        Map<String, Object> reorderAvailable = Map.of(
                Variables.ServiceTaskOrderBike.ORDER_ID.getValue(), "ORDER-2",
                Variables.ServiceTaskOrderBike.BIKE_AVAILABLE.getValue(), true);
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_ORDER_BIKE, reorderAvailable);

        correlateMessage(engine, Messages.MIRAVELO_HANDOVER_REPORTED, businessKey);
        fireTimer(engine, Elements.EVENT_WITHDRAWAL_PERIOD_ELAPSED);
        completeExternalTask(engine, ServiceTasks.BIKE_LEASING_ACTIVATE_LEASING);

        assertThat(instance)
                .isEnded()
                .hasPassed(
                        Elements.USER_TASK_CLARIFY_ALTERNATIVE.getValue(),
                        Elements.SERVICE_TASK_ORDER_BIKE.getValue(),
                        Elements.SERVICE_TASK_ACTIVATE_LEASING.getValue(),
                        Elements.END_EVENT_LEASING_ACTIVE.getValue())
                .hasNotPassed(
                        Elements.END_EVENT_CONTRACT_CANCELLED.getValue(),
                        Elements.END_EVENT_APPLICATION_REJECTED.getValue());
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
