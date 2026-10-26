package io.miragon.blueprint.adapter.inbound.workflow;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.application.port.inbound.CancelContractUseCase;
import io.miragon.blueprint.domain.leasing.ContractId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.junit.jupiter.api.Test;

class CancelContractTaskTest {

    private final CancelContractUseCase useCase = mock(CancelContractUseCase.class);
    private final CancelContractTask task = new CancelContractTask(useCase);

    @Test
    void handsTheApplicationOfTheWorkflowAggregateToTheUseCase() {
        // given
        LeasingApplication application = testLeasingApplication().contractId(new ContractId("CONTRACT-1")).build();

        // when
        task.cancelContract(LeasingApplicationAggregate.of(application));

        // then
        verify(useCase).cancelContract(application);
    }
}
