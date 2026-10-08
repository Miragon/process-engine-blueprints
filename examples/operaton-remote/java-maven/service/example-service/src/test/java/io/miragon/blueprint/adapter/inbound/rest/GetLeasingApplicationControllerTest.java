package io.miragon.blueprint.adapter.inbound.rest;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.miragon.blueprint.application.port.inbound.GetLeasingApplicationQuery;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.ContractId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(GetLeasingApplicationController.class)
class GetLeasingApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetLeasingApplicationQuery query;

    @Test
    void returnsTheApplicationWithItsResolvedBikeModelWhenItExists() throws Exception {

        // given: a fully populated application the query can find, with its bike model resolved
        LeasingApplication application = testLeasingApplication()
                .orderId(new OrderId("ORDER-1"))
                .contractId(new ContractId("CONTRACT-1"))
                .build();
        when(query.byId(application.id()))
                .thenReturn(new GetLeasingApplicationQuery.Result(application, "Gravel Explorer 900"));

        // when: the request is performed
        MvcResult response = mockMvc
                .perform(get("/api/bike-leasing/{applicationId}", application.id().value().toString()))
                .andReturn();

        // then: the response is 200 and carries every mapped field of the application
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        assertThat(response.getResponse().getContentAsString())
                .contains(
                        application.id().value().toString(),
                        application.customerName().value(),
                        application.email().value(),
                        application.bikeId().value(),
                        "Gravel Explorer 900",
                        "RECEIVED",
                        "ORDER-1",
                        "CONTRACT-1");
        verify(query).byId(application.id());
        verifyNoMoreInteractions(query);
    }

    @Test
    void returns404WhenTheApplicationDoesNotExist() throws Exception {

        // given: an unknown application id
        ApplicationId id = ApplicationId.of("123e4567-e89b-12d3-a456-426614174000");
        when(query.byId(id)).thenReturn(null);

        // when: the request is performed
        MvcResult response = mockMvc
                .perform(get("/api/bike-leasing/{applicationId}", id.value().toString()))
                .andReturn();

        // then: the response is 404 Not Found
        assertThat(response.getResponse().getStatus()).isEqualTo(404);
    }
}
