package io.miragon.blueprint.adapter.inbound.operaton;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.BikeUnavailableException;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    void completesWithTheOrderIdAsOutputVariable() {

        // given: the bike was available and an order was placed
        when(useCase.orderBike(applicationId)).thenReturn(new OrderId("ORDER-1"));

        // when: the worker runs
        underTest.execute(task, service);

        // then: the process continues with the `orderId` output variable and no BPMN error is raised
        verify(service).complete(task, Map.of("orderId", "ORDER-1"));
        verify(service, never()).handleBpmnError(any(ExternalTask.class), anyString(), anyString());
    }

    @Test
    void raisesTheBikeUnavailableBpmnErrorWhenTheDealerCannotDeliverTheBike() {

        // given: the dealer has the bike out of stock
        when(useCase.orderBike(applicationId)).thenThrow(new BikeUnavailableException(new BikeId("BIKE-OOS")));

        // when: the worker runs
        underTest.execute(task, service);

        // then: the BPMN error is raised and the task is neither completed nor reported as a failure
        verify(service).handleBpmnError(task, "bikeUnavailable", "Bike BIKE-OOS is not available at the dealer");
        verify(service, never()).complete(any(ExternalTask.class), anyMap());
        verify(service, never()).handleFailure(any(ExternalTask.class), anyString(), anyString(), anyInt(), anyLong());
    }
}
