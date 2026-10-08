package io.miragon.blueprint.adapter.inbound.workflow;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.application.port.inbound.IssueInsurancePolicyUseCase;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.junit.jupiter.api.Test;

class IssueInsurancePolicyTaskTest {

    private final IssueInsurancePolicyUseCase useCase = mock(IssueInsurancePolicyUseCase.class);
    private final IssueInsurancePolicyTask task = new IssueInsurancePolicyTask(useCase);

    @Test
    void delegatesToTheUseCaseForTheApplicationOfTheWorkflowAggregate() {
        // given
        LeasingApplication application = testLeasingApplication().build();

        // when
        task.issueInsurancePolicy(LeasingApplicationAggregate.of(application));

        // then
        verify(useCase).issuePolicy(application.id());
    }
}
