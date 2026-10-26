package io.miragon.blueprint.adapter.outbound.db;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.application.port.outbound.TaskInboxPort;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(TaskInboxAdapter.class)
class TaskInboxAdapterTest {

    @Autowired
    private TaskInboxAdapter underTest;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void listsTheApplicationsParkedOnAnOpenClarificationWithTheTimeTheyStartedWaiting() {

        // given: one application waiting for a clarification, one whose clarification was resolved and one untouched
        LeasingApplicationAggregate waiting = aggregate();
        waiting.clarificationRequested("4711", LocalDateTime.of(2026, 3, 1, 8, 0));
        LeasingApplicationAggregate resolved = aggregate();
        resolved.clarificationRequested("4712", LocalDateTime.of(2026, 3, 2, 8, 0));
        resolved.resolveClarification(true);
        List.of(waiting, resolved, aggregate()).forEach(entityManager::persist);
        entityManager.flush();
        entityManager.clear();

        // when: the inbox is read
        List<TaskInboxPort.OpenClarification> openClarifications = underTest.findOpenClarifications();

        // then: only the waiting application is listed
        assertThat(openClarifications).containsExactly(
                new TaskInboxPort.OpenClarification(
                        ApplicationId.of(waiting.getApplicationId()),
                        LocalDateTime.of(2026, 3, 1, 8, 0)));
    }

    @Test
    void theInboxIsEmptyWhileNoClarificationIsOpen() {

        // given: an application that never needed a clarification
        entityManager.persist(aggregate());
        entityManager.flush();
        entityManager.clear();

        // when / then: nothing is listed
        assertThat(underTest.findOpenClarifications()).isEmpty();
    }

    private static LeasingApplicationAggregate aggregate() {
        return LeasingApplicationAggregate.of(testLeasingApplication().id(new ApplicationId(UUID.randomUUID())).build());
    }
}
