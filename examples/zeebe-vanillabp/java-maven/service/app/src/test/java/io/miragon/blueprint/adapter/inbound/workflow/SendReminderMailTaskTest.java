package io.miragon.blueprint.adapter.inbound.workflow;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.application.port.inbound.SendSignatureReminderUseCase;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.junit.jupiter.api.Test;

class SendReminderMailTaskTest {

    private final SendSignatureReminderUseCase useCase = mock(SendSignatureReminderUseCase.class);
    private final SendReminderMailTask task = new SendReminderMailTask(useCase);

    @Test
    void handsTheApplicationOfTheWorkflowAggregateToTheUseCase() {
        // given
        LeasingApplication application = testLeasingApplication().build();

        // when
        task.sendReminderMail(LeasingApplicationAggregate.of(application));

        // then
        verify(useCase).sendSignatureReminder(application);
    }
}
