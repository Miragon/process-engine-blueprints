package io.miragon.blueprint.application.service;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;

import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import org.junit.jupiter.api.Test;

class ActivateLeasingServiceTest {

    private final ActivateLeasingService underTest = new ActivateLeasingService();

    @Test
    void activateReturnsTheApplicationWithTheActiveStatus() {

        // given: a handed-over application
        LeasingApplication application = testLeasingApplication().status(LeasingStatus.HANDED_OVER).build();

        // when: the leasing is activated
        LeasingApplication activated = underTest.activate(application);

        // then: only the status moved to ACTIVE
        assertThat(activated).isEqualTo(testLeasingApplication().status(LeasingStatus.ACTIVE).build());
    }
}
