package io.miragon.blueprint.adapter.outbound.zeebe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.bpmcrafters.processengineapi.task.TaskInformation;
import dev.bpmcrafters.processengineapi.task.support.UserTaskSupport;
import io.miragon.blueprint.application.port.outbound.TaskInboxPort;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskInboxAdapterTest {

    private final UserTaskSupport userTaskSupport = mock(UserTaskSupport.class);
    private final TaskInboxAdapter underTest = new TaskInboxAdapter(userTaskSupport);

    private final String applicationId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000").toString();

    @Test
    void translates_pooled_tasks_into_their_application_id_and_creation_date() {
        // given: one delivered task carrying the application id
        when(userTaskSupport.getAllTasks()).thenReturn(List.of(task("task-1", "2026-06-23T09:00:00Z")));
        when(userTaskSupport.getPayload("task-1")).thenReturn(Map.of("applicationId", applicationId));

        // when: the inbox is read
        List<TaskInboxPort.OpenClarification> result = underTest.findOpenClarifications();

        // then: the task surfaces as its case, never as an engine task id
        assertThat(result).containsExactly(new TaskInboxPort.OpenClarification(
                ApplicationId.of(applicationId),
                LocalDateTime.of(2026, 6, 23, 9, 0)));
    }

    @Test
    void skips_a_task_that_left_the_pool_while_the_inbox_is_read() {
        // given: the payload of a listed task is already gone
        when(userTaskSupport.getAllTasks()).thenReturn(List.of(task("task-gone", "2026-06-23T09:00:00Z")));
        when(userTaskSupport.getPayload("task-gone")).thenThrow(new IllegalArgumentException("unknown task"));

        // when / then
        assertThat(underTest.findOpenClarifications()).isEmpty();
    }

    @Test
    void skips_a_task_without_an_application_id() {
        // given
        when(userTaskSupport.getAllTasks()).thenReturn(List.of(task("task-1", "2026-06-23T09:00:00Z")));
        when(userTaskSupport.getPayload("task-1")).thenReturn(Map.of());

        // when / then
        assertThat(underTest.findOpenClarifications()).isEmpty();
    }

    @Test
    void skips_a_task_without_a_creation_date() {
        // given
        when(userTaskSupport.getAllTasks())
                .thenReturn(List.of(new TaskInformation("task-1", Map.of("activityId", "userTask_clarifyAlternative"))));
        when(userTaskSupport.getPayload("task-1")).thenReturn(Map.of("applicationId", applicationId));

        // when / then
        assertThat(underTest.findOpenClarifications()).isEmpty();
    }

    @Test
    void skips_a_task_of_another_user_task() {
        // given: an open clarify-return task of the same application in the pool
        when(userTaskSupport.getAllTasks())
                .thenReturn(List.of(task("task-1", "2026-06-23T09:00:00Z", "userTask_clarifyReturn")));
        when(userTaskSupport.getPayload("task-1")).thenReturn(Map.of("applicationId", applicationId));

        // when / then: the inbox lists clarify-alternative tasks only
        assertThat(underTest.findOpenClarifications()).isEmpty();
    }

    private static TaskInformation task(String taskId, String creationDate) {
        return task(taskId, creationDate, "userTask_clarifyAlternative");
    }

    private static TaskInformation task(String taskId, String creationDate, String activityId) {
        return new TaskInformation(taskId, Map.of("activityId", activityId, "creationDate", creationDate));
    }
}
