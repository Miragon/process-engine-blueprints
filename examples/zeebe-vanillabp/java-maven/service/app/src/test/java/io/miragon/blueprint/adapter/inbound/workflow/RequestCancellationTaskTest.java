package io.miragon.blueprint.adapter.inbound.workflow;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase;
import io.miragon.blueprint.domain.bike.OrderId;
import org.junit.jupiter.api.Test;

class RequestCancellationTaskTest {

    private final RequestOrderCancellationUseCase useCase = mock(RequestOrderCancellationUseCase.class);
    private final RequestCancellationTask task = new RequestCancellationTask(useCase);

    @Test
    void recordsOnTheWorkflowAggregateThatTheDealerAllowsTheCancellation() {
        // given
        LeasingApplicationAggregate aggregate =
                LeasingApplicationAggregate.of(testLeasingApplication().orderId(new OrderId("ORDER-1")).build());
        when(useCase.requestCancellation(new OrderId("ORDER-1"))).thenReturn(true);

        // when
        task.requestCancellation(aggregate);

        // then
        assertThat(aggregate.getCancellationPossible()).isTrue();
    }

    @Test
    void recordsOnTheWorkflowAggregateThatTheDealerRefusesTheCancellation() {
        // given
        LeasingApplicationAggregate aggregate =
                LeasingApplicationAggregate.of(testLeasingApplication().orderId(new OrderId("ORDER-1")).build());
        when(useCase.requestCancellation(new OrderId("ORDER-1"))).thenReturn(false);

        // when
        task.requestCancellation(aggregate);

        // then
        assertThat(aggregate.getCancellationPossible()).isFalse();
        verify(useCase).requestCancellation(new OrderId("ORDER-1"));
    }

    @Test
    void failsWhenNoOrderWasPlaced() {
        // given
        LeasingApplicationAggregate aggregate =
                LeasingApplicationAggregate.of(testLeasingApplication().orderId(null).build());

        // when / then
        assertThatThrownBy(() -> task.requestCancellation(aggregate)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(useCase);
    }
}
