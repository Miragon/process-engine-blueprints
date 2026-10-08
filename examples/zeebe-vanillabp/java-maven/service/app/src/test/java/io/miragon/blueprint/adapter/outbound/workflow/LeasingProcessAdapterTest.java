package io.miragon.blueprint.adapter.outbound.workflow;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.adapter.outbound.db.LeasingApplicationJpaRepository;
import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.vanillabp.spi.process.ProcessService;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LeasingProcessAdapterTest {

    @SuppressWarnings("unchecked")
    private final ProcessService<LeasingApplicationAggregate> processService = mock(ProcessService.class);
    private final LeasingApplicationJpaRepository aggregates = mock(LeasingApplicationJpaRepository.class);
    private final LeasingProcessAdapter underTest = new LeasingProcessAdapter(processService, aggregates);

    private final LeasingApplication application = testLeasingApplication().build();
    private final LeasingApplicationAggregate aggregate = LeasingApplicationAggregate.of(application);

    @Test
    void submitRequestStartsTheWorkflowByTheLeasingRequestMessageWithTheApplicationAsItsAggregate() {

        // given: a process service that accepts the start
        ArgumentCaptor<LeasingApplicationAggregate> started = ArgumentCaptor.forClass(LeasingApplicationAggregate.class);

        // when: the request is submitted
        underTest.submitRequest(application);

        // then: the workflow is started for an aggregate of that application
        verify(processService).startWorkflowByMessage(started.capture(), eq("miravelo.leasingRequestReceived"));
        assertThat(started.getValue().toDomain()).isEqualTo(application);
        verifyNoMoreInteractions(processService, aggregates);
    }

    @Test
    void correlateContractSignedCorrelatesTheContractSignedMessageToTheWorkflowOfTheApplication() {

        // given: the stored aggregate of the application
        knownAggregate();

        // when: the signature is correlated
        underTest.correlateContractSigned(application.id());

        // then: the message is correlated for exactly that aggregate
        verify(processService).correlateMessage(aggregate, "miravelo.contractSigned");
        verifyNoMoreInteractions(processService);
    }

    @Test
    void correlateHandoverReportedCorrelatesTheHandoverReportedMessageToTheWorkflowOfTheApplication() {

        // given: the stored aggregate of the application
        knownAggregate();

        // when: the handover is correlated
        underTest.correlateHandoverReported(application.id());

        // then: the message is correlated for exactly that aggregate
        verify(processService).correlateMessage(aggregate, "miravelo.handoverReported");
        verifyNoMoreInteractions(processService);
    }

    @Test
    void correlateApplicationWithdrawnCorrelatesTheApplicationWithdrawnMessageToTheWorkflowOfTheApplication() {

        // given: the stored aggregate of the application
        knownAggregate();

        // when: the withdrawal is correlated
        underTest.correlateApplicationWithdrawn(application.id());

        // then: the message is correlated for exactly that aggregate
        verify(processService).correlateMessage(aggregate, "miravelo.applicationWithdrawn");
        verifyNoMoreInteractions(processService);
    }

    @Test
    void correlatingForAnUnknownApplicationFailsWithoutTouchingTheEngine() {

        // given: no aggregate for the application
        when(aggregates.findById(application.id().value().toString())).thenReturn(Optional.empty());

        // when / then: the correlation is refused
        assertThatThrownBy(() -> underTest.correlateContractSigned(application.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(application.id().value().toString());
        verifyNoInteractions(processService);
    }

    @Test
    void completeAlternativeClarificationRecordsTheDecisionAndCompletesTheRememberedUserTask() {

        // given: an aggregate parked on the clarification user task
        aggregate.clarificationRequested("4711", LocalDateTime.of(2026, 3, 1, 8, 0));
        knownAggregate();

        // when: the clarification is completed with an alternative
        underTest.completeAlternativeClarification(application.id(), true);

        // then: the decision is on the aggregate and the remembered task is completed
        assertThat(aggregate.getAlternativeFound()).isTrue();
        assertThat(aggregate.getClarifyAlternativeTaskId()).isNull();
        verify(processService).completeUserTask(aggregate, "4711");
        verifyNoMoreInteractions(processService);
    }

    @Test
    void completeAlternativeClarificationIsNotAvailableWhileNoClarificationIsOpen() {

        // given: an aggregate that is not parked on the clarification user task
        knownAggregate();

        // when / then: the action is refused without touching the engine
        assertThatThrownBy(() -> underTest.completeAlternativeClarification(application.id(), false))
                .isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(processService);
    }

    private void knownAggregate() {
        when(aggregates.findById(application.id().value().toString())).thenReturn(Optional.of(aggregate));
    }
}
