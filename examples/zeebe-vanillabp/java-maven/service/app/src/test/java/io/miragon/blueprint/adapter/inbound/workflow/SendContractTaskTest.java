package io.miragon.blueprint.adapter.inbound.workflow;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.application.port.inbound.SendContractUseCase;
import io.miragon.blueprint.domain.leasing.ContractId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.junit.jupiter.api.Test;

class SendContractTaskTest {

    private final SendContractUseCase useCase = mock(SendContractUseCase.class);
    private final SendContractTask task = new SendContractTask(useCase);

    @Test
    void adoptsTheIssuedContractOnTheWorkflowAggregate() {
        // given
        LeasingApplication application = testLeasingApplication().build();
        LeasingApplicationAggregate aggregate = LeasingApplicationAggregate.of(application);
        when(useCase.sendContract(application)).thenReturn(application.withContract(new ContractId("CONTRACT-1")));

        // when
        task.sendContract(aggregate);

        // then
        assertThat(aggregate.getContractId()).isEqualTo("CONTRACT-1");
    }
}
