package io.miragon.blueprint.adapter.inbound.workflow;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.application.port.inbound.CancelInsurancePolicyUseCase;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.junit.jupiter.api.Test;

class CancelPolicyTaskTest {

    private final CancelInsurancePolicyUseCase useCase = mock(CancelInsurancePolicyUseCase.class);
    private final CancelPolicyTask task = new CancelPolicyTask(useCase);

    @Test
    void delegatesToTheUseCaseForTheApplicationOfTheWorkflowAggregate() {
        // given
        LeasingApplication application = testLeasingApplication().build();

        // when
        task.cancelPolicy(LeasingApplicationAggregate.of(application));

        // then
        verify(useCase).cancelPolicy(application.id());
    }
}
