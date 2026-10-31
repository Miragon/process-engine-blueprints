package io.miragon.blueprint.adapter.outbound.temporal

import io.miragon.blueprint.adapter.process.BIKE_LEASING_TASK_QUEUE
import io.miragon.blueprint.adapter.process.BikeLeasingWorkflow
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.leasing.CustomerName
import io.miragon.blueprint.domain.leasing.Email
import io.miragon.blueprint.domain.leasing.LeasingApplication
import io.miragon.common.temporal.config.kotlinDataConverter
import io.mockk.mockk
import io.mockk.verify
import io.temporal.client.WorkflowClientOptions
import io.temporal.testing.TestEnvironmentOptions
import io.temporal.testing.TestWorkflowEnvironment
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.LocalDateTime

@Timeout(60)
class LeasingProcessAdapterTest {

    private val env = TestWorkflowEnvironment.newInstance(
        TestEnvironmentOptions.newBuilder()
            .setWorkflowClientOptions(WorkflowClientOptions.newBuilder().setDataConverter(kotlinDataConverter()).build())
            .build(),
    )
    private val adapter = LeasingProcessAdapter(env.workflowClient)
    private val workflow = mockk<BikeLeasingWorkflow>(relaxed = true)
    private val application = LeasingApplication.receive(
        id = ApplicationId.new(),
        customerName = CustomerName("Max Mustermann"),
        email = Email("max@example.com"),
        age = 40,
        monthlyNetIncome = 3000.0,
        bikeId = BikeId("BIKE-1"),
        createdAt = LocalDateTime.now(),
    )

    @BeforeEach
    fun startWorker() {
        env.newWorker(BIKE_LEASING_TASK_QUEUE).addWorkflowImplementationFactory(BikeLeasingWorkflow::class.java) { workflow }
        env.start()
    }

    @AfterEach
    fun closeEnvironment() = env.close()

    @Test
    fun `starts the workflow under the application id`() {
        adapter.submitRequest(application)

        verify(timeout = 10_000) {
            workflow.run(match { it.applicationId == application.id.value.toString() && it.bikeId == "BIKE-1" })
        }
    }

    @Test
    fun `drops signals for a workflow that does not exist`() {
        assertThatCode { adapter.correlateContractSigned(application.id) }.doesNotThrowAnyException()
        assertThatCode { adapter.correlateHandoverReported(application.id) }.doesNotThrowAnyException()
        assertThatCode { adapter.correlateApplicationWithdrawn(application.id) }.doesNotThrowAnyException()
    }

    @Test
    fun `reports a clarification without a workflow as not found`() {
        assertThatThrownBy { adapter.completeAlternativeClarification(application.id, alternativeFound = false) }
            .isInstanceOf(NoSuchElementException::class.java)
    }
}
