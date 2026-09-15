package io.miragon.blueprint.adapter.inbound.operaton;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.operaton.bpm.client.task.ExternalTask;
import org.operaton.bpm.client.task.ExternalTaskService;

class OrderBikeWorkerTest {

    private final OrderBikeUseCase useCase = mock(OrderBikeUseCase.class);
    private final OrderBikeWorker underTest = new OrderBikeWorker(useCase);

    private final ApplicationId applicationId =
            new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
    private final ExternalTask task = mock(ExternalTask.class);
    private final ExternalTaskService service = mock(ExternalTaskService.class);

    @BeforeEach
    void setUp() {
        when(task.getBusinessKey()).thenReturn(applicationId.value().toString());
    }

    @Test
    void completesWithTheOrderIdAndAvailabilityAsOutputVariables() {

        // given: the bike was available and an order was placed
        when(useCase.orderBike(applicationId))
                .thenReturn(new OrderBikeUseCase.Result(new OrderId("ORDER-1"), true));

        // when: the worker runs
        underTest.execute(task, service);

        // then: the process continues with the `orderId` and `bikeAvailable` output variables
        verify(service).complete(
                eq(task),
                ArgumentMatchers.<Map<String, Object>>argThat(it ->
                        "ORDER-1".equals(it.get("orderId")) && Boolean.TRUE.equals(it.get("bikeAvailable"))));
    }
}
