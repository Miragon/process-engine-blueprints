package io.miragon.blueprint.adapter.process

import com.ninjasquad.springmockk.MockkBean
import io.miragon.blueprint.application.port.inbound.ActivateLeasingUseCase
import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase
import io.miragon.blueprint.application.port.inbound.CancelContractUseCase
import io.miragon.blueprint.application.port.inbound.CancelInsurancePolicyUseCase
import io.miragon.blueprint.application.port.inbound.IssueInsurancePolicyUseCase
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.miragon.blueprint.application.port.inbound.RejectApplicationUseCase
import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase
import io.miragon.blueprint.application.port.inbound.SendCancellationConfirmationUseCase
import io.miragon.blueprint.application.port.inbound.SendContractUseCase
import io.miragon.blueprint.application.port.inbound.SendSignatureReminderUseCase
import io.miragon.blueprint.application.port.outbound.LeasingProcess
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import io.mockk.every
import org.operaton.bpm.client.ExternalTaskClient
import org.operaton.bpm.engine.ProcessEngine
import org.operaton.bpm.engine.delegate.ExecutionListener
import org.operaton.bpm.engine.delegate.TaskListener
import org.operaton.bpm.engine.runtime.ProcessInstance
import org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.init
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.ServerSocket

/**
 * Base of the integration process tests: the worker's Spring context with an in-memory Operaton engine
 * serving `/engine-rest` on a free port. The real workers and the real process adapter reach it over HTTP,
 * as they reach the remote engine in production; only the use cases behind the workers are mocked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test", "in-memory-engine")
@Import(InMemoryEngineIntegrationTest.EngineListeners::class)
abstract class InMemoryEngineIntegrationTest {

    @Autowired
    protected lateinit var process: LeasingProcess

    @Autowired
    protected lateinit var engine: ProcessEngine

    @MockkBean(relaxed = true)
    protected lateinit var rejectApplicationUseCase: RejectApplicationUseCase

    @MockkBean(relaxed = true)
    protected lateinit var sendContractUseCase: SendContractUseCase

    @MockkBean(relaxed = true)
    protected lateinit var cancelContractUseCase: CancelContractUseCase

    @MockkBean(relaxed = true)
    protected lateinit var issueInsurancePolicyUseCase: IssueInsurancePolicyUseCase

    @MockkBean(relaxed = true)
    protected lateinit var cancelInsurancePolicyUseCase: CancelInsurancePolicyUseCase

    @MockkBean(relaxed = true)
    protected lateinit var sendSignatureReminderUseCase: SendSignatureReminderUseCase

    @MockkBean(relaxed = true)
    protected lateinit var sendCancellationConfirmationUseCase: SendCancellationConfirmationUseCase

    @MockkBean(relaxed = true)
    protected lateinit var requestOrderCancellationUseCase: RequestOrderCancellationUseCase

    @MockkBean(relaxed = true)
    protected lateinit var bookCancellationCostsUseCase: BookCancellationCostsUseCase

    @MockkBean(relaxed = true)
    protected lateinit var orderBikeUseCase: OrderBikeUseCase

    @MockkBean(relaxed = true)
    protected lateinit var activateLeasingUseCase: ActivateLeasingUseCase

    @BeforeEach
    fun initEngineAndPlaceOrdersSuccessfully() {
        init(engine)
        every { orderBikeUseCase.orderBike(any(), any()) } returns OrderId("ORDER-1")
    }

    protected fun submit(age: Int, income: Double): ApplicationId {
        val application = testLeasingApplication(ApplicationId.new(), age = age, monthlyNetIncome = income)
        process.submitRequest(application)
        return application.id
    }

    protected fun instanceOf(id: ApplicationId): ProcessInstance = engine.findInstance(id.value.toString())

    /** No-op stand-ins for the listeners the model references; their real beans live in `engine-service`. */
    @TestConfiguration
    class EngineListeners {

        @Bean
        fun bikeOrderAuditListener() = ExecutionListener { }

        @Bean
        fun clarifyAlternativeTaskListener() = TaskListener { }
    }

    companion object {
        val REQUESTED_BIKE = testLeasingApplication().bikeId

        private val engineRestPort = ServerSocket(0).use { it.localPort }
        private val engineRestUrl = "http://localhost:$engineRestPort/engine-rest"

        @JvmStatic
        @DynamicPropertySource
        fun pointTheWorkerAtTheInMemoryEngine(registry: DynamicPropertyRegistry) {
            registry.add("server.port") { engineRestPort }
            registry.add("bike-leasing.engine-rest-base-url") { engineRestUrl }
            registry.add("operaton.bpm.client.base-url") { engineRestUrl }
        }

        /** Stops polling before the in-memory engine goes down with the test JVM. */
        @JvmStatic
        @AfterAll
        fun stopPolling(@Autowired client: ExternalTaskClient) = client.stop()
    }
}
