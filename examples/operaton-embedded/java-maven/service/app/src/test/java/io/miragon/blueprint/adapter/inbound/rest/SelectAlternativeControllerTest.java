package io.miragon.blueprint.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.miragon.blueprint.application.port.inbound.SelectAlternativeUseCase;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(SelectAlternativeController.class)
class SelectAlternativeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SelectAlternativeUseCase useCase;

    @Test
    @DisplayName("client clarifies an alternative bike")
    void clientClarifiesAnAlternativeBike() throws Exception {

        // given: a decision body and the application-id path variable
        String pathVar = "123e4567-e89b-12d3-a456-426614174000";
        String body = """
                {"alternativeFound":true,"bikeId":"BIKE-ALT","bikeModel":"Aero Road 700"}""";

        // when: the request is performed
        MvcResult response = mockMvc.perform(
                        post("/api/bike-leasing/{applicationId}/clarify-alternative", pathVar)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andReturn();

        // then: the use case is invoked with the mapped command and the response is 202 Accepted
        assertThat(response.getResponse().getStatus()).isEqualTo(202);
        verify(useCase).selectAlternative(
                new SelectAlternativeUseCase.Command(
                        ApplicationId.of(pathVar), true, new BikeId("BIKE-ALT"), "Aero Road 700"));
        verifyNoMoreInteractions(useCase);
    }

    @Test
    @DisplayName("client reports that no alternative was found")
    void clientReportsThatNoAlternativeWasFound() throws Exception {

        // given: a decision body with no alternative and no bike details
        String pathVar = "123e4567-e89b-12d3-a456-426614174000";
        String body = """
                {"alternativeFound":false}""";

        // when: the request is performed
        MvcResult response = mockMvc.perform(
                        post("/api/bike-leasing/{applicationId}/clarify-alternative", pathVar)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andReturn();

        // then: the use case is invoked with a command carrying no alternative bike and the response is 202
        assertThat(response.getResponse().getStatus()).isEqualTo(202);
        verify(useCase).selectAlternative(
                new SelectAlternativeUseCase.Command(ApplicationId.of(pathVar), false, null, null));
        verifyNoMoreInteractions(useCase);
    }

    @Test
    @DisplayName("rejects an accepted alternative without a bike id with a 400 problem detail")
    void rejectsAnAcceptedAlternativeWithoutABikeIdWithA400ProblemDetail() throws Exception {

        // given: a decision body that accepts an alternative but names no bike
        String pathVar = "123e4567-e89b-12d3-a456-426614174000";
        String body = """
                {"alternativeFound":true}""";

        // when: the request is performed
        MvcResult response = mockMvc.perform(
                        post("/api/bike-leasing/{applicationId}/clarify-alternative", pathVar)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andReturn();

        // then: the request is refused before the use case is reached
        assertThat(response.getResponse().getStatus()).isEqualTo(400);
        assertThat(response.getResponse().getContentType()).contains("application/problem+json");
        assertThat(response.getResponse().getContentAsString()).contains("An accepted alternative must name the bike");
        verifyNoMoreInteractions(useCase);
    }

    @Test
    @DisplayName("a decision without the alternative-found flag is rejected")
    void aDecisionWithoutTheAlternativeFoundFlagIsRejected() throws Exception {

        // given: a decision body that names a bike but not whether an alternative was found
        String pathVar = "123e4567-e89b-12d3-a456-426614174000";
        String body = """
                {"bikeId":"BIKE-ALT","bikeModel":"Aero Road 700"}""";

        // when: the request is performed
        MvcResult response = mockMvc.perform(
                        post("/api/bike-leasing/{applicationId}/clarify-alternative", pathVar)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andReturn();

        // then: it is an unreadable request and never reaches the use case
        assertThat(response.getResponse().getStatus()).isEqualTo(400);
        assertThat(response.getResponse().getContentType()).contains("application/problem+json");
        assertThat(response.getResponse().getContentAsString()).contains("Failed to read request");
        verifyNoMoreInteractions(useCase);
    }
}
