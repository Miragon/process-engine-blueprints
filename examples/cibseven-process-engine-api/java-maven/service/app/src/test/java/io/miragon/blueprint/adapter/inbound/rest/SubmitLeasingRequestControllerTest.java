package io.miragon.blueprint.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.miragon.blueprint.application.port.inbound.SubmitLeasingRequestUseCase;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.CustomerName;
import io.miragon.blueprint.domain.leasing.Email;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(SubmitLeasingRequestController.class)
class SubmitLeasingRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubmitLeasingRequestUseCase useCase;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("user submits a leasing request")
    void user_submits_a_leasing_request() throws Exception {

        // given: valid input data & rest-operation
        Map<String, Object> input = validInput();
        ApplicationId applicationId = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        SubmitLeasingRequestUseCase.Command expectedCommand = new SubmitLeasingRequestUseCase.Command(
                new CustomerName("John Doe"),
                new Email("john.doe@test.com"),
                35,
                3500.0,
                new BikeId("BIKE-900"),
                "Gravel Explorer 900");
        when(useCase.submit(any())).thenReturn(applicationId);

        // when: the request is performed
        MvcResult response = mockMvc
                .perform(post("/api/bike-leasing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input)))
                .andReturn();

        // then: the use case is invoked with the mapped command and the id is returned
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        assertThat(response.getResponse().getContentAsString()).contains(applicationId.value().toString());
        verify(useCase).submit(expectedCommand);
        verifyNoMoreInteractions(useCase);
    }

    @ParameterizedTest
    @ValueSource(strings = {"customerName", "email", "age", "monthlyNetIncome", "bikeId", "bikeModel"})
    @DisplayName("a request without a required field is rejected")
    void a_request_without_a_required_field_is_rejected(String missingField) throws Exception {

        // given: an otherwise valid request that lacks one required field
        Map<String, Object> input = validInput();
        input.remove(missingField);

        // when: the request is performed
        MvcResult response = mockMvc
                .perform(post("/api/bike-leasing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input)))
                .andReturn();

        // then: it is an unreadable request and never reaches the use case
        assertThat(response.getResponse().getStatus()).isEqualTo(400);
        assertThat(response.getResponse().getContentType()).contains("application/problem+json");
        assertThat(response.getResponse().getContentAsString()).contains("Failed to read request");
        verifyNoInteractions(useCase);
    }

    @ParameterizedTest
    @ValueSource(strings = {"customerName", "email", "bikeId", "bikeModel"})
    @DisplayName("a request with a required text field set to null is rejected as unreadable")
    void a_request_with_a_required_text_field_set_to_null_is_rejected_as_unreadable(String nullField) throws Exception {

        // given: an otherwise valid request that carries null for one required field
        Map<String, Object> input = validInput();
        input.put(nullField, null);

        // when: the request is performed
        MvcResult response = mockMvc
                .perform(post("/api/bike-leasing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input)))
                .andReturn();

        // then: it is an unreadable request and never reaches the use case
        assertThat(response.getResponse().getStatus()).isEqualTo(400);
        assertThat(response.getResponse().getContentType()).contains("application/problem+json");
        assertThat(response.getResponse().getContentAsString()).contains("Failed to read request");
        verifyNoInteractions(useCase);
    }

    private static Map<String, Object> validInput() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("customerName", "John Doe");
        input.put("email", "john.doe@test.com");
        input.put("age", 35);
        input.put("monthlyNetIncome", 3500.0);
        input.put("bikeId", "BIKE-900");
        input.put("bikeModel", "Gravel Explorer 900");
        return input;
    }
}
