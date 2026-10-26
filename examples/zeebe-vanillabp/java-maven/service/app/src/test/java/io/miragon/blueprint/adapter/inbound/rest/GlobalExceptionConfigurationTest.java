package io.miragon.blueprint.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.miragon.blueprint.application.port.inbound.ReportHandoverUseCase;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(ReportHandoverController.class)
class GlobalExceptionConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportHandoverUseCase useCase;

    @Test
    void aMissingClarificationTaskMapsTo409WithProblemJson() throws Exception {

        // given: the clarification user task can no longer be located (token moved past the wait state)
        String pathVar = "123e4567-e89b-12d3-a456-426614174000";
        doThrow(new NoSuchElementException("No active clarify-alternative task for " + pathVar))
                .when(useCase).reportHandover(any());

        // when: the POST is performed
        MvcResult response = mockMvc
                .perform(post("/api/bike-leasing/{applicationId}/report-handover", pathVar))
                .andReturn();

        // then: 409 Conflict is returned as application/problem+json
        assertThat(response.getResponse().getStatus()).isEqualTo(409);
        assertThat(response.getResponse().getContentType()).contains(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    }
}
