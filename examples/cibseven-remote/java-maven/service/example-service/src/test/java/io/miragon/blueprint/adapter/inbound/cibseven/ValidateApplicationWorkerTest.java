package io.miragon.blueprint.adapter.inbound.cibseven;

import io.miragon.blueprint.application.port.inbound.ValidateApplicationUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.ApplicationInvalidException;
import org.cibseven.bpm.client.task.ExternalTask;
import org.cibseven.bpm.client.task.ExternalTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ValidateApplicationWorkerTest {

    private final ValidateApplicationUseCase useCase = mock(ValidateApplicationUseCase.class);
    private final ValidateApplicationWorker underTest = new ValidateApplicationWorker(useCase);

    private final ApplicationId applicationId = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
    private final ExternalTask task = mock(ExternalTask.class);
    private final ExternalTaskService service = mock(ExternalTaskService.class);

    @BeforeEach
    void stubBusinessKey() {
        when(task.getBusinessKey()).thenReturn(applicationId.value().toString());
    }

    @Test
    @DisplayName("completes the task for a valid application")
    void completesTheTaskForAValidApplication() {

        // given: validation succeeds
        doNothing().when(useCase).validate(applicationId);

        // when: the worker runs
        underTest.execute(task, service);

        // then: the external task is completed and no BPMN error is raised
        verify(useCase).validate(applicationId);
        verify(service).complete(task);
        verify(service, never()).handleBpmnError(any(), any(), any());
    }

    @Test
    @DisplayName("raises the applicationInvalid BPMN error when validation fails")
    void raisesTheApplicationInvalidBpmnErrorWhenValidationFails() {

        // given: validation surfaces the application as invalid
        doThrow(new ApplicationInvalidException(applicationId, "no income"))
                .when(useCase).validate(applicationId);

        // when: the worker runs
        underTest.execute(task, service);

        // then: the BPMN error is raised and the task is not completed
        verify(service).handleBpmnError(task, "applicationInvalid", "no income");
        verify(service, never()).complete(any());
    }
}
