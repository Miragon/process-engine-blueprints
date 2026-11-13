package io.miragon.blueprint.adapter.outbound.zeebe;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.bpmcrafters.processengineapi.Empty;
import dev.bpmcrafters.processengineapi.correlation.CorrelateMessageCmd;
import dev.bpmcrafters.processengineapi.correlation.CorrelationApi;
import dev.bpmcrafters.processengineapi.process.ProcessInformation;
import dev.bpmcrafters.processengineapi.process.StartProcessApi;
import dev.bpmcrafters.processengineapi.process.StartProcessByMessageCmd;
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd;
import dev.bpmcrafters.processengineapi.task.TaskInformation;
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi;
import dev.bpmcrafters.processengineapi.task.support.UserTaskSupport;
import io.miragon.blueprint.adapter.process.Messages;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LeasingProcessAdapterTest {

    private final StartProcessApi startProcessApi = mock(StartProcessApi.class);
    private final CorrelationApi correlationApi = mock(CorrelationApi.class);
    private final UserTaskSupport userTaskSupport = mock(UserTaskSupport.class);
    private final UserTaskCompletionApi userTaskCompletionApi = mock(UserTaskCompletionApi.class);
    private final LeasingProcessAdapter underTest = new LeasingProcessAdapter(
            startProcessApi,
            correlationApi,
            userTaskSupport,
            userTaskCompletionApi);

    private final ApplicationId id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));

    @Test
    void submitRequest_starts_the_process_by_message_with_the_application_variables() {
        // given: a leasing application and a captured start command
        LeasingApplication application = testLeasingApplication().id(id).build();
        when(startProcessApi.startProcess(any()))
                .thenReturn(CompletableFuture.completedFuture(mock(ProcessInformation.class)));

        // when: the request is submitted
        underTest.submitRequest(application);

        // then: the leasing-request message starts the process with the DMN inputs and the correlation variable
        ArgumentCaptor<StartProcessByMessageCmd> cmd = ArgumentCaptor.forClass(StartProcessByMessageCmd.class);
        verify(startProcessApi).startProcess(cmd.capture());
        assertThat(cmd.getValue().getMessageName()).isEqualTo(Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.getValue());
        assertThat(cmd.getValue().get()).isEqualTo(Map.of(
                "applicationId", id.value().toString(),
                "bikeId", "BIKE-900",
                "monthlyNetIncome", 3500.0,
                "age", 35));
    }

    @Test
    void completeAlternativeClarification_resolves_the_task_by_application_id_and_completes_it() {
        // given: an open task delivered to the user-task pool for this application
        when(userTaskSupport.getAllTasks()).thenReturn(List.of(task("task-1")));
        when(userTaskSupport.getPayload("task-1")).thenReturn(Map.of("applicationId", id.value().toString()));
        when(userTaskCompletionApi.completeTask(any())).thenReturn(CompletableFuture.completedFuture(Empty.INSTANCE));

        // when: an alternative bike is selected from the outside
        underTest.completeAlternativeClarification(id, true, new BikeId("BIKE-42"));

        // then: the resolved task is completed with the decision and the chosen bike
        ArgumentCaptor<CompleteTaskCmd> cmd = ArgumentCaptor.forClass(CompleteTaskCmd.class);
        verify(userTaskCompletionApi).completeTask(cmd.capture());
        assertThat(cmd.getValue().getTaskId()).isEqualTo("task-1");
        assertThat(cmd.getValue().get()).isEqualTo(Map.of("alternativeFound", true, "bikeId", "BIKE-42"));
    }

    @Test
    void completeAlternativeClarification_skips_tasks_of_other_applications_and_unreadable_payloads() {
        // given: a stale task whose payload lookup fails and a task of another application, before the matching one
        when(userTaskSupport.getAllTasks()).thenReturn(List.of(task("task-stale"), task("task-other"), task("task-1")));
        when(userTaskSupport.getPayload("task-stale")).thenThrow(new IllegalArgumentException("unknown task"));
        when(userTaskSupport.getPayload("task-other")).thenReturn(Map.of("applicationId", UUID.randomUUID().toString()));
        when(userTaskSupport.getPayload("task-1")).thenReturn(Map.of("applicationId", id.value().toString()));
        when(userTaskCompletionApi.completeTask(any())).thenReturn(CompletableFuture.completedFuture(Empty.INSTANCE));

        // when: the clarification ends without an alternative
        underTest.completeAlternativeClarification(id, false, null);

        // then: only the matching task is completed, with the decision only
        ArgumentCaptor<CompleteTaskCmd> cmd = ArgumentCaptor.forClass(CompleteTaskCmd.class);
        verify(userTaskCompletionApi, times(1)).completeTask(cmd.capture());
        assertThat(cmd.getValue().getTaskId()).isEqualTo("task-1");
        assertThat(cmd.getValue().get()).isEqualTo(Map.of("alternativeFound", false));
    }

    @Test
    void completeAlternativeClarification_waits_for_the_task_to_be_delivered() {
        // given: the task reaches the pool on the second look
        when(userTaskSupport.getAllTasks()).thenReturn(List.of()).thenReturn(List.of(task("task-1")));
        when(userTaskSupport.getPayload("task-1")).thenReturn(Map.of("applicationId", id.value().toString()));
        when(userTaskCompletionApi.completeTask(any())).thenReturn(CompletableFuture.completedFuture(Empty.INSTANCE));

        // when
        underTest.completeAlternativeClarification(id, true, null);

        // then
        ArgumentCaptor<CompleteTaskCmd> cmd = ArgumentCaptor.forClass(CompleteTaskCmd.class);
        verify(userTaskCompletionApi).completeTask(cmd.capture());
        assertThat(cmd.getValue().getTaskId()).isEqualTo("task-1");
        verify(userTaskSupport, times(2)).getAllTasks();
    }

    @Test
    void completeAlternativeClarification_fails_with_NoSuchElementException_when_no_task_appears() {
        // given: the token is not at the task, so the pool stays empty
        when(userTaskSupport.getAllTasks()).thenReturn(List.of());

        // when / then: the REST advice maps this to a 409, and nothing is completed
        assertThatThrownBy(() -> underTest.completeAlternativeClarification(id, true, null))
                .isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(userTaskCompletionApi);
    }

    @Test
    void completeAlternativeClarification_keeps_the_interrupt_and_fails_unchecked_when_the_wait_is_interrupted() {
        // given: no task in the pool yet and an interrupted caller
        when(userTaskSupport.getAllTasks()).thenReturn(List.of());
        Thread.currentThread().interrupt();
        try {
            // when / then: the wait aborts at once with an unchecked exception, which rolls back the use case
            assertThatThrownBy(() -> underTest.completeAlternativeClarification(id, true, null))
                    .isExactlyInstanceOf(IllegalStateException.class)
                    .hasCauseInstanceOf(InterruptedException.class);
            // and: the interrupt flag stays visible to the caller
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        verify(userTaskSupport, times(1)).getAllTasks();
        verifyNoInteractions(userTaskCompletionApi);
    }

    @Test
    void completeReturnClarification_completes_the_clarify_return_task_of_the_application_without_variables() {
        // given: a stale clarify-alternative task and the open clarify-return task of this application in the pool
        when(userTaskSupport.getAllTasks())
                .thenReturn(List.of(task("task-1"), task("task-2", "userTask_clarifyReturn")));
        when(userTaskSupport.getPayload(any())).thenReturn(Map.of("applicationId", id.value().toString()));
        when(userTaskCompletionApi.completeTask(any())).thenReturn(CompletableFuture.completedFuture(Empty.INSTANCE));

        // when: the back office confirms the return
        underTest.completeReturnClarification(id);

        // then: only the clarify-return task is completed, without variables
        ArgumentCaptor<CompleteTaskCmd> cmd = ArgumentCaptor.forClass(CompleteTaskCmd.class);
        verify(userTaskCompletionApi, times(1)).completeTask(cmd.capture());
        assertThat(cmd.getValue().getTaskId()).isEqualTo("task-2");
        assertThat(cmd.getValue().get()).isEmpty();
    }

    @Test
    void correlateContractSigned_publishes_the_message_with_the_application_id_as_correlation_key() {
        assertCorrelation(Messages.MIRAVELO_CONTRACT_SIGNED.getValue(), () -> underTest.correlateContractSigned(id));
    }

    @Test
    void correlateHandoverReported_publishes_the_message_with_the_application_id_as_correlation_key() {
        assertCorrelation(Messages.MIRAVELO_HANDOVER_REPORTED.getValue(), () -> underTest.correlateHandoverReported(id));
    }

    @Test
    void correlateApplicationWithdrawn_publishes_the_message_with_the_application_id_as_correlation_key() {
        assertCorrelation(Messages.MIRAVELO_APPLICATION_WITHDRAWN.getValue(), () -> underTest.correlateApplicationWithdrawn(id));
    }

    private void assertCorrelation(String messageName, Runnable correlate) {
        when(correlationApi.correlateMessage(any())).thenReturn(CompletableFuture.completedFuture(Empty.INSTANCE));

        correlate.run();

        ArgumentCaptor<CorrelateMessageCmd> cmd = ArgumentCaptor.forClass(CorrelateMessageCmd.class);
        verify(correlationApi).correlateMessage(cmd.capture());
        assertThat(cmd.getValue().getMessageName()).isEqualTo(messageName);
        assertThat(cmd.getValue().getCorrelation().get().getCorrelationKey()).isEqualTo(id.value().toString());
        // The adapter rejects every restriction except the tenant id.
        assertThat(cmd.getValue().getRestrictions()).isEmpty();
    }

    private static TaskInformation task(String taskId) {
        return task(taskId, "userTask_clarifyAlternative");
    }

    private static TaskInformation task(String taskId, String activityId) {
        return new TaskInformation(taskId, Map.of("activityId", activityId));
    }
}
