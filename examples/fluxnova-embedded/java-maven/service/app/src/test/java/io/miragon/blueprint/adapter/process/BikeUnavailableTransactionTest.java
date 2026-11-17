package io.miragon.blueprint.adapter.process;

import static io.miragon.blueprint.adapter.process.ProcessInstanceUtils.findProcessInstance;
import static org.finos.fluxnova.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.finos.fluxnova.bpm.engine.test.assertions.bpmn.BpmnAwareTests.execute;
import static org.finos.fluxnova.bpm.engine.test.assertions.bpmn.BpmnAwareTests.init;
import static org.finos.fluxnova.bpm.engine.test.assertions.bpmn.BpmnAwareTests.job;

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
import org.finos.fluxnova.bpm.engine.ProcessEngine;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs the out-of-stock order with the <strong>real</strong> use cases instead of mocks. The embedded
 * engine and the {@code @Transactional} services share one transaction, so the
 * {@code BikeUnavailableException} must not mark it rollback-only — otherwise the
 * {@code bikeUnavailable} BPMN error could never be committed and the order job would fail into an
 * incident. {@link BikeLeasingProcessTest} mocks the use cases and therefore cannot see this.
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
    void bikeUnavailableTheBpmnErrorRaisedByTheRealOrderServiceIsCommitted() {
        // given
        // BIKE-OOS is on the simulated dealer's out-of-stock list
        LeasingApplication application = LeasingApplication.receive(
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

        // when
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance));
        process.correlateContractSigned(application.id());
        execute(job(FlowNodes.EventContractSigned.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, instance));
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskOrderBike.ELEMENT_ID, instance)); // unavailable -> BPMN error -> clarify alternative

        // then
        assertThat(instance)
                .isWaitingAt(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID)
                .hasPassed(FlowNodes.EventBikeUnavailable.ELEMENT_ID);
    }

    @Test
    void bikeUnavailableTheBikeTheProcessCarriesIsStoredAlthoughTheOrderServiceThrows() {
        // given
        // the stored application names another bike than the out-of-stock one the process carries
        LeasingApplication application = LeasingApplication.receive(
                ApplicationId.newId(),
                new CustomerName("Test Customer"),
                new Email("test@example.com"),
                35,
                3500.0,
                new BikeId("BIKE-900"),
                LocalDateTime.now());
        repository.save(application);
        process.submitRequest(application.selectAlternative(new BikeId("BIKE-OOS")));
        ProcessInstance instance = findProcessInstance(runtimeService, application.id());

        // when
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance));
        process.correlateContractSigned(application.id());
        execute(job(FlowNodes.EventContractSigned.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, instance));
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskOrderBike.ELEMENT_ID, instance)); // unavailable -> BPMN error -> clarify alternative

        // then
        Assertions.assertThat(repository.findById(application.id()))
                .map(LeasingApplication::bikeId)
                .contains(new BikeId("BIKE-OOS"));
    }

    @Test
    void bikeUnavailableTheAlternativeReachesTheApplicationThroughTheProcessVariableAlone() {
        // given
        LeasingApplication application = LeasingApplication.receive(
                ApplicationId.newId(),
                new CustomerName("Test Customer"),
                new Email("test@example.com"),
                35,
                3500.0,
                new BikeId("BIKE-900"),
                LocalDateTime.now());
        repository.save(application);
        process.submitRequest(application.selectAlternative(new BikeId("BIKE-OOS")));
        ProcessInstance instance = findProcessInstance(runtimeService, application.id());
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.StartEventLeasingRequestReceived.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskSendContract.ELEMENT_ID, instance));
        process.correlateContractSigned(application.id());
        execute(job(FlowNodes.EventContractSigned.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, instance));
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskOrderBike.ELEMENT_ID, instance)); // unavailable -> BPMN error -> clarify alternative

        // when
        process.completeAlternativeClarification(application.id(), true, new BikeId("BIKE-ALT"));
        execute(job(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID, instance));
        execute(job(FlowNodes.ServiceTaskOrderBike.ELEMENT_ID, instance));
        execute(job(FlowNodes.GatewayJoin.ELEMENT_ID, instance));

        // then
        LeasingApplication reordered = repository.findById(application.id()).orElseThrow();
        Assertions.assertThat(reordered.bikeId()).isEqualTo(new BikeId("BIKE-ALT"));
        Assertions.assertThat(reordered.status()).isEqualTo(LeasingStatus.ORDERED);
    }
}
