package io.miragon.blueprint.adapter.process

import io.camunda.client.CamundaClient
import io.camunda.client.api.search.enums.UserTaskState
import io.camunda.process.test.api.CamundaAssert
import io.camunda.process.test.api.CamundaSpringProcessTest
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors
import io.miragon.blueprint.adapter.outbound.db.LeasingApplicationJpaRepository
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes
import io.miragon.blueprint.application.port.inbound.SelectAlternativeUseCase
import io.miragon.blueprint.application.port.inbound.SignContractUseCase
import io.miragon.blueprint.application.port.inbound.SubmitLeasingRequestUseCase
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.leasing.CustomerName
import io.miragon.blueprint.domain.leasing.Email
import io.miragon.blueprint.domain.leasing.LeasingStatus
import io.miragon.common.test.assertions.hasCompletedElements
import io.miragon.common.test.config.WorkflowDeploymentTestConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Duration

/**
 * Runs the out-of-stock order with the **real** use cases instead of mocks. The order service throws
 * the `BikeUnavailableException` inside the transaction VanillaBP's workflow task runs in; that must
 * not keep the task from reporting the `bikeUnavailable` BPMN error to the engine, nor from persisting
 * the workflow aggregate — otherwise the order task would fail into an incident. Declining the
 * alternative afterwards must cancel the application without cancelling an order that was never
 * placed. [BikeLeasingProcessTest] mocks the use cases and therefore cannot see this.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@CamundaSpringProcessTest
@ActiveProfiles("test")
@Import(WorkflowDeploymentTestConfiguration::class)
class BikeUnavailableTransactionTest {

    @Autowired
    private lateinit var camundaClient: CamundaClient

    @Autowired
    private lateinit var aggregates: LeasingApplicationJpaRepository

    @Autowired
    private lateinit var submitLeasingRequestUseCase: SubmitLeasingRequestUseCase

    @Autowired
    private lateinit var signContractUseCase: SignContractUseCase

    @Autowired
    private lateinit var selectAlternativeUseCase: SelectAlternativeUseCase

    @Test
    fun `bike unavailable - the BPMN error raised by the real order service reaches the engine`() {
        // BIKE-OOS is on the simulated dealer's out-of-stock list
        val id = submitOutOfStockRequest()
        val instanceKey = awaitProcessInstance()
        val instance = ProcessInstanceSelectors.byKey(instanceKey)

        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.ServiceTaskSendContract)
        signContractUseCase.signContract(id)

        // the order raises bikeUnavailable -> parks on the clarify-alternative user task
        CamundaAssert.assertThatProcessInstance(instance)
            .hasCompletedElements(FlowNodes.ServiceTaskIssueInsurancePolicy, FlowNodes.EventBikeUnavailable)
        awaitClarificationRequested(instanceKey)
        assertThat(incidentsOf(instanceKey)).isZero()
        val parked = aggregateOf(id)
        assertThat(parked.clarifyAlternativeTaskId).isNotNull()
        assertThat(parked.orderId).isNull()

        // declining the alternative reverses the application without an order to cancel
        selectAlternativeUseCase.selectAlternative(
            SelectAlternativeUseCase.Command(applicationId = id, alternativeFound = false),
        )

        CamundaAssert.assertThatProcessInstance(instance).isCompleted()
        CamundaAssert.assertThatProcessInstance(instance)
            .hasCompletedElements(FlowNodes.ServiceTaskConfirmContractCancellation, FlowNodes.EndEventContractCancelled)
            .hasNotActivatedElements(FlowNodes.CallActivityCancelBikeOrder.ELEMENT_ID)
        assertThat(orderCancellationsOf(instanceKey)).isZero()
        assertThat(incidentsOf(instanceKey)).isZero()
        assertThat(aggregateOf(id).status).isEqualTo(LeasingStatus.CANCELLED)
    }

    private fun submitOutOfStockRequest(): ApplicationId =
        submitLeasingRequestUseCase.submit(
            SubmitLeasingRequestUseCase.Command(
                customerName = CustomerName("Test Customer"),
                email = Email("test@example.com"),
                age = 35,
                monthlyNetIncome = 3500.0,
                bikeId = BikeId("BIKE-OOS"),
                bikeModel = "Out Of Stock 100",
            ),
        )

    private fun aggregateOf(id: ApplicationId): LeasingApplicationAggregate =
        aggregates.findById(id.value.toString()).orElseThrow()

    private fun awaitProcessInstance(): Long {
        var instanceKey = 0L
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(200))
            .untilAsserted {
                val instances = camundaClient.newProcessInstanceSearchRequest()
                    .filter { it.processDefinitionId(BikeLeasingProcessProcessApi.PROCESS_ID.value) }
                    .send()
                    .join()
                    .items()
                assert(instances.isNotEmpty()) { "no bike-leasing process instance was started" }
                instanceKey = instances.first().processInstanceKey
            }
        return instanceKey
    }

    private fun awaitClarificationRequested(processInstanceKey: Long) {
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(200))
            .untilAsserted {
                val tasks = camundaClient.newUserTaskSearchRequest()
                    .filter {
                        it.elementId(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID)
                        it.state(UserTaskState.CREATED)
                        it.processInstanceKey(processInstanceKey)
                    }
                    .send()
                    .join()
                    .items()
                assert(tasks.isNotEmpty()) { "the clarify-alternative user task was not created" }
            }
    }

    private fun orderCancellationsOf(processInstanceKey: Long): Int =
        camundaClient.newProcessInstanceSearchRequest()
            .filter {
                it.processDefinitionId(CancelBikeOrderProcessApi.PROCESS_ID.value)
                it.parentProcessInstanceKey(processInstanceKey)
            }
            .send()
            .join()
            .items()
            .size

    private fun incidentsOf(processInstanceKey: Long): Int =
        camundaClient.newIncidentSearchRequest()
            .filter { it.processInstanceKey(processInstanceKey) }
            .send()
            .join()
            .items()
            .size
}
