package io.miragon.blueprint.application.service;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.miragon.blueprint.application.port.outbound.NotificationPort;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import org.junit.jupiter.api.Test;

class RejectApplicationServiceTest {

    private final NotificationPort notification = mock(NotificationPort.class);
    private final RejectApplicationService underTest = new RejectApplicationService(notification);

    @Test
    void rejectNotifiesTheCustomerAndReturnsTheApplicationAsRejected() {

        // given: an application
        LeasingApplication application = testLeasingApplication().build();

        // when: the application is rejected
        LeasingApplication rejected = underTest.reject(application);

        // then: the customer is notified and only the status moved to REJECTED
        assertThat(rejected).isEqualTo(testLeasingApplication().status(LeasingStatus.REJECTED).build());
        verify(notification).send("Your bike-leasing application was rejected", application);
        verifyNoMoreInteractions(notification);
    }
}
