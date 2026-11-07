package io.miragon.blueprint.adapter.process;

import static io.miragon.blueprint.adapter.process.JobExecutionUtils.continueToNextWaitState;
import static io.miragon.blueprint.adapter.process.ProcessInstanceUtils.findProcessInstance;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.init;

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes;
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.LeasingProcess;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.CustomerName;
import io.miragon.blueprint.domain.leasing.Email;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import java.time.LocalDateTime;
import org.assertj.core.api.Assertions;
import org.cibseven.bpm.engine.ProcessEngine;
import org.cibseven.bpm.engine.RuntimeService;
import org.cibseven.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs the out-of-stock order with the <strong>real</strong> use cases instead of mocks. The
 * {@code @Transactional} order service throws the {@code BikeUnavailableException} out of its transaction;
 * that must not keep the {@code @ProcessEngineWorker} from reporting the {@code bikeUnavailable} BPMN error
 * to the engine — otherwise the order task would fail into an incident — and it must not undo the bike the
 * service stored before.
 * {@link BikeLeasingProcessTest} mocks the use cases and therefore cannot see this.
 */
@SpringBootTest
@ActiveProfiles("test")
class BikeUnavailableTransactionTest {

    @Autowired
    private LeasingProcess process;

    @Autowired
    private LeasingApplicationRepository repository;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private ProcessEngine processEngine;

    @BeforeEach
    void setUp() {
        init(processEngine);
    }

    @Test
    @DisplayName("bike unavailable - the BPMN error raised by the real order service reaches the engine")
    void bikeUnavailableTheBpmnErrorRaisedByTheRealOrderServiceReachesTheEngine()
        throws InterruptedException {
        // BIKE-OOS is on the simulated dealer's out-of-stock list
        LeasingApplication application =
            LeasingApplication.receive(
                ApplicationId.newId(),
                new CustomerName("Test Customer"),
                new Email("test@example.com"),
                35,
                3500.0,
                new BikeId("BIKE-OOS"),
                LocalDateTime.now());
        repository.save(application);
        process.submitRequest(application);
        ProcessInstance instance = findProcessInstance(runtimeService, application.id());

        continueToNextWaitState(processEngine); // parks on the signature wait state
        process.correlateContractSigned(application.id());
        continueToNextWaitState(processEngine); // fork -> order raises bikeUnavailable -> parks on clarify-alternative

        assertThat(instance)
            .isWaitingAt(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID)
            .hasPassed(FlowNodes.EventBikeUnavailable.ELEMENT_ID);
        Assertions.assertThat(runtimeService.createIncidentQuery().processInstanceId(instance.getId()).count())
            .isZero();
    }

    @Test
    @DisplayName("alternative selected - the chosen bike is ordered and stored on the application")
    void alternativeSelectedTheChosenBikeIsOrderedAndStoredOnTheApplication()
        throws InterruptedException {
        LeasingApplication application = receivedApplication(new BikeId("BIKE-OOS"));
        repository.save(application);
        process.submitRequest(application);
        ProcessInstance instance = findProcessInstance(runtimeService, application.id());
        signContractAndRunIntoTheUnavailableBike(application.id());

        process.completeAlternativeClarification(application.id(), true, new BikeId("BIKE-900"));
        continueToNextWaitState(processEngine); // re-order succeeds -> parallel join -> handover wait state

        assertThat(instance).isWaitingAt(FlowNodes.EventHandoverReported.ELEMENT_ID);
        LeasingApplication stored = repository.findById(application.id()).orElseThrow();
        Assertions.assertThat(stored.bikeId()).isEqualTo(new BikeId("BIKE-900"));
        Assertions.assertThat(stored.status()).isEqualTo(LeasingStatus.ORDERED);
    }

    @Test
    @DisplayName("bike unavailable - the bike the process carries stays on the application although the order fails")
    void bikeUnavailableTheBikeTheProcessCarriesStaysOnTheApplicationAlthoughTheOrderFails()
        throws InterruptedException {
        LeasingApplication application = receivedApplication(new BikeId("BIKE-900"));
        repository.save(application);
        process.submitRequest(application.selectAlternative(new BikeId("BIKE-OOS")));
        ProcessInstance instance = findProcessInstance(runtimeService, application.id());

        signContractAndRunIntoTheUnavailableBike(application.id());

        assertThat(instance).isWaitingAt(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID);
        Assertions.assertThat(repository.findById(application.id()).orElseThrow().bikeId())
            .isEqualTo(new BikeId("BIKE-OOS"));
    }

    private void signContractAndRunIntoTheUnavailableBike(ApplicationId id) throws InterruptedException {
        continueToNextWaitState(processEngine); // parks on the signature wait state
        process.correlateContractSigned(id);
        continueToNextWaitState(processEngine); // fork -> order raises bikeUnavailable -> parks on clarify-alternative
    }

    private LeasingApplication receivedApplication(BikeId bikeId) {
        return LeasingApplication.receive(
            ApplicationId.newId(),
            new CustomerName("Test Customer"),
            new Email("test@example.com"),
            35,
            3500.0,
            bikeId,
            LocalDateTime.now());
    }
}
