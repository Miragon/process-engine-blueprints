package io.miragon.blueprint.adapter.inbound.workflow;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase;
import io.miragon.blueprint.domain.bike.OrderId;
import org.junit.jupiter.api.Test;

class BookCostsTaskTest {

    private final BookCancellationCostsUseCase useCase = mock(BookCancellationCostsUseCase.class);
    private final BookCostsTask task = new BookCostsTask(useCase);

    @Test
    void booksTheCostsForTheOrderRecordedOnTheWorkflowAggregate() {
        // given
        LeasingApplicationAggregate aggregate =
                LeasingApplicationAggregate.of(testLeasingApplication().orderId(new OrderId("ORDER-1")).build());

        // when
        task.bookCosts(aggregate);

        // then
        verify(useCase).bookCosts(new OrderId("ORDER-1"));
    }

    @Test
    void failsWhenNoOrderWasPlaced() {
        // given
        LeasingApplicationAggregate aggregate =
                LeasingApplicationAggregate.of(testLeasingApplication().orderId(null).build());

        // when / then
        assertThatThrownBy(() -> task.bookCosts(aggregate)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(useCase);
    }
}
