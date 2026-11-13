package io.miragon.blueprint.adapter.process;

import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.init;
import static org.mockito.ArgumentMatchers.any;
import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.mockito.Mockito.when;

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
import io.miragon.blueprint.application.port.outbound.LeasingProcess;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import org.operaton.bpm.client.ExternalTaskClient;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.delegate.ExecutionListener;
import org.operaton.bpm.engine.delegate.TaskListener;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Base of the integration process tests: the worker's Spring context with an in-memory Operaton engine
 * serving {@code /engine-rest} on a free port. The real workers and the real process adapter reach it over HTTP,
 * as they reach the remote engine in production; only the use cases behind the workers are mocked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles({"test", "in-memory-engine"})
@Import(InMemoryEngineIntegrationTest.EngineListeners.class)
abstract class InMemoryEngineIntegrationTest {

    static final BikeId REQUESTED_BIKE = testLeasingApplication().build().bikeId();

    private static final int ENGINE_REST_PORT = freePort();
    private static final String ENGINE_REST_URL = "http://localhost:" + ENGINE_REST_PORT + "/engine-rest";

    @Autowired
    protected LeasingProcess process;

    @Autowired
    protected ProcessEngine engine;

    @MockitoBean
    protected RejectApplicationUseCase rejectApplicationUseCase;

    @MockitoBean
    protected SendContractUseCase sendContractUseCase;

    @MockitoBean
    protected CancelContractUseCase cancelContractUseCase;

    @MockitoBean
    protected IssueInsurancePolicyUseCase issueInsurancePolicyUseCase;

    @MockitoBean
    protected CancelInsurancePolicyUseCase cancelInsurancePolicyUseCase;

    @MockitoBean
    protected SendSignatureReminderUseCase sendSignatureReminderUseCase;

    @MockitoBean
    protected SendCancellationConfirmationUseCase sendCancellationConfirmationUseCase;

    @MockitoBean
    protected RequestOrderCancellationUseCase requestOrderCancellationUseCase;

    @MockitoBean
    protected BookCancellationCostsUseCase bookCancellationCostsUseCase;

    @MockitoBean
    protected OrderBikeUseCase orderBikeUseCase;

    @MockitoBean
    protected ActivateLeasingUseCase activateLeasingUseCase;

    @DynamicPropertySource
    static void pointTheWorkerAtTheInMemoryEngine(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> ENGINE_REST_PORT);
        registry.add("bike-leasing.engine-rest-base-url", () -> ENGINE_REST_URL);
        registry.add("operaton.bpm.client.base-url", () -> ENGINE_REST_URL);
    }

    /** Stops polling before the in-memory engine goes down with the test JVM. */
    @AfterAll
    static void stopPolling(@Autowired ExternalTaskClient client) {
        client.stop();
    }

    @BeforeEach
    void initEngineAndPlaceOrdersSuccessfully() {
        init(engine);
        when(orderBikeUseCase.orderBike(any(), any())).thenReturn(new OrderId("ORDER-1"));
    }

    protected ApplicationId submit(int age, double income) {
        LeasingApplication application = testLeasingApplication().id(ApplicationId.newId()).age(age).monthlyNetIncome(income).build();
        process.submitRequest(application);
        return application.id();
    }

    protected ProcessInstance instanceOf(ApplicationId id) {
        return ProcessDriveUtils.findInstance(engine, id.value().toString());
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** No-op stand-ins for the listeners the model references; their real beans live in {@code engine-service}. */
    @TestConfiguration
    static class EngineListeners {

        @Bean
        ExecutionListener bikeOrderAuditListener() {
            return execution -> { };
        }

        @Bean
        TaskListener clarifyAlternativeTaskListener() {
            return delegateTask -> { };
        }
    }
}
