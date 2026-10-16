package io.miragon.blueprint.adapter.process;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import io.miragon.blueprint.adapter.outbound.db.LeasingApplicationJpaRepository;
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes;
import io.miragon.blueprint.application.port.inbound.SelectAlternativeUseCase;
import io.miragon.blueprint.application.port.inbound.SignContractUseCase;
import io.miragon.blueprint.application.port.inbound.SubmitLeasingRequestUseCase;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.CustomerName;
import io.miragon.blueprint.domain.leasing.Email;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import io.miragon.common.test.config.WorkflowDeploymentTestConfiguration;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs the out-of-stock order with the <strong>real</strong> use cases instead of mocks. The order
 * service throws the {@code BikeUnavailableException} inside the transaction VanillaBP's workflow task
 * runs in; that must not keep the task from reporting the {@code bikeUnavailable} BPMN error to the
 * engine, nor from persisting the workflow aggregate — otherwise the order task would fail into an
 * incident. Declining the alternative afterwards must cancel the application without cancelling an
 * order that was never placed. {@link BikeLeasingProcessTest} mocks the use cases and therefore cannot
 * see this.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@CamundaSpringProcessTest
@ActiveProfiles("test")
@Import(WorkflowDeploymentTestConfiguration.class)
class BikeUnavailableTransactionTest {

    @Autowired
    private CamundaClient camundaClient;

    @Autowired
    private LeasingApplicationJpaRepository aggregates;

    @Autowired
    private SubmitLeasingRequestUseCase submitLeasingRequestUseCase;

    @Autowired
    private SignContractUseCase signContractUseCase;

    @Autowired
    private SelectAlternativeUseCase selectAlternativeUseCase;

    @Test
    void bikeUnavailableTheBpmnErrorRaisedByTheRealOrderServiceReachesTheEngine() {
        // BIKE-OOS is on the simulated dealer's out-of-stock list
        ApplicationId id = submitOutOfStockRequest();
        long instanceKey = awaitProcessInstance();
        ProcessInstanceSelector instance = ProcessInstanceSelectors.byKey(instanceKey);

        CamundaAssert.assertThatProcessInstance(instance)
                .hasCompletedElements(FlowNodes.ServiceTaskSendContract.ELEMENT_ID);
        signContractUseCase.signContract(id);

        // the order raises bikeUnavailable -> parks on the clarify-alternative user task
        CamundaAssert.assertThatProcessInstance(instance)
                .hasCompletedElements(
                        FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID,
                        FlowNodes.EventBikeUnavailable.ELEMENT_ID);
        awaitClarificationRequested(instanceKey);
        assertThat(incidentsOf(instanceKey)).isZero();
        LeasingApplicationAggregate parked = aggregateOf(id);
        assertThat(parked.getClarifyAlternativeTaskId()).isNotNull();
        assertThat(parked.getOrderId()).isNull();

        // declining the alternative reverses the application without an order to cancel
        selectAlternativeUseCase.selectAlternative(new SelectAlternativeUseCase.Command(id, false, null, null));

        CamundaAssert.assertThatProcessInstance(instance).isCompleted();
        CamundaAssert.assertThatProcessInstance(instance)
                .hasCompletedElements(
                        FlowNodes.ServiceTaskConfirmContractCancellation.ELEMENT_ID,
                        FlowNodes.EndEventContractCancelled.ELEMENT_ID)
                .hasNotActivatedElements(FlowNodes.CallActivityCancelBikeOrder.ELEMENT_ID);
        assertThat(orderCancellationsOf(instanceKey)).isZero();
        assertThat(incidentsOf(instanceKey)).isZero();
        assertThat(aggregateOf(id).getStatus()).isEqualTo(LeasingStatus.CANCELLED);
    }

    private ApplicationId submitOutOfStockRequest() {
        return submitLeasingRequestUseCase.submit(
                new SubmitLeasingRequestUseCase.Command(
                        new CustomerName("Test Customer"),
                        new Email("test@example.com"),
                        35,
                        3500.0,
                        new BikeId("BIKE-OOS"),
                        "Out Of Stock 100"));
    }

    private LeasingApplicationAggregate aggregateOf(ApplicationId id) {
        return aggregates.findById(id.value().toString()).orElseThrow();
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

    private void awaitClarificationRequested(long processInstanceKey) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    var tasks = camundaClient.newUserTaskSearchRequest()
                            .filter(filter -> {
                                filter.elementId(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID);
                                filter.state(UserTaskState.CREATED);
                                filter.processInstanceKey(processInstanceKey);
                            })
                            .send()
                            .join()
                            .items();
                    assertThat(tasks).as("the clarify-alternative user task was not created").isNotEmpty();
                });
    }

    private int orderCancellationsOf(long processInstanceKey) {
        return camundaClient.newProcessInstanceSearchRequest()
                .filter(filter -> {
                    filter.processDefinitionId(CancelBikeOrderProcessApi.PROCESS_ID.getValue());
                    filter.parentProcessInstanceKey(processInstanceKey);
                })
                .send()
                .join()
                .items()
                .size();
    }

    private int incidentsOf(long processInstanceKey) {
        return camundaClient.newIncidentSearchRequest()
                .filter(filter -> filter.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items()
                .size();
    }
}
