package io.miragon.blueprint.adapter.outbound.db;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@ActiveProfiles("test")
@Import(LeasingApplicationPersistenceAdapter.class)
class LeasingApplicationPersistenceAdapterTest {

    @Autowired
    private LeasingApplicationPersistenceAdapter underTest;

    @Autowired
    private TestEntityManager entityManager;

    private final ApplicationId id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));

    @Test
    void savesAndReloadsAnApplication() {

        // given: a leasing application
        LeasingApplication application = testLeasingApplication().id(id).build();

        // when: it is saved and re-read from a cleared persistence context
        underTest.save(application);
        entityManager.flush();
        entityManager.clear();

        // then: the reloaded application equals the original
        assertThat(underTest.findById(id)).get().usingRecursiveComparison().isEqualTo(application);
    }

    @Test
    void savingAKnownApplicationKeepsTheStateTheWorkflowRecordedOnItsAggregate() {

        // given: a stored application whose aggregate is parked on an open clarification
        LeasingApplicationAggregate aggregate = LeasingApplicationAggregate.of(testLeasingApplication().id(id).build());
        aggregate.clarificationRequested("4711", LocalDateTime.of(2026, 3, 1, 8, 0));
        entityManager.persist(aggregate);
        entityManager.flush();
        entityManager.clear();

        // when: the domain saves the application again
        underTest.save(testLeasingApplication().id(id).status(LeasingStatus.WITHDRAWN).build());
        entityManager.flush();
        entityManager.clear();

        // then: the domain change is stored and the open clarification survives
        LeasingApplicationAggregate reloaded = entityManager.find(LeasingApplicationAggregate.class, id.value().toString());
        assertThat(reloaded.getStatus()).isEqualTo(LeasingStatus.WITHDRAWN);
        assertThat(reloaded.getClarifyAlternativeTaskId()).isEqualTo("4711");
    }

    @Test
    void findByIdReturnsNothingWhenTheApplicationDoesNotExist() {

        // given: an empty database
        // when / then: the lookup returns nothing
        assertThat(underTest.findById(id)).isEmpty();
    }

    @Test
    @Sql("classpath:sql/leasing-application.sql")
    void findByIdMapsAnExistingRowBackToTheDomain() {

        // given: a pre-inserted row (see sql/leasing-application.sql)
        // when / then: the adapter maps it back to the expected application
        assertThat(underTest.findById(id))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(testLeasingApplication().id(id).build());
    }

    @Test
    void findAllReturnsAPageOrderedByCreationDateNewestFirst() {

        // given: three applications created on different days
        LeasingApplication older = applicationCreatedAt(LocalDateTime.of(2026, 1, 1, 8, 0));
        LeasingApplication newer = applicationCreatedAt(LocalDateTime.of(2026, 3, 1, 8, 0));
        LeasingApplication newest = applicationCreatedAt(LocalDateTime.of(2026, 6, 1, 8, 0));
        List.of(older, newest, newer).forEach(underTest::save);
        entityManager.flush();
        entityManager.clear();

        // when: the first page is read without a filter
        LeasingApplicationRepository.Page page = underTest.findAll(new LeasingApplicationRepository.Criteria(null, 0, 10));

        // then: all three come back, newest first
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.items()).extracting(LeasingApplication::id).containsExactly(newest.id(), newer.id(), older.id());
    }

    @Test
    void findAllFiltersByStatusAndPaginates() {

        // given: two RECEIVED and one ACTIVE application
        underTest.save(applicationInStatus(LeasingStatus.RECEIVED));
        underTest.save(applicationInStatus(LeasingStatus.RECEIVED));
        underTest.save(applicationInStatus(LeasingStatus.ACTIVE));
        entityManager.flush();
        entityManager.clear();

        // when: only ACTIVE applications are requested
        LeasingApplicationRepository.Page page =
                underTest.findAll(new LeasingApplicationRepository.Criteria(LeasingStatus.ACTIVE, 0, 10));

        // then: only the single ACTIVE application is returned
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().status()).isEqualTo(LeasingStatus.ACTIVE);
    }

    private static LeasingApplication applicationCreatedAt(LocalDateTime createdAt) {
        return testLeasingApplication().id(new ApplicationId(UUID.randomUUID())).createdAt(createdAt).build();
    }

    private static LeasingApplication applicationInStatus(LeasingStatus status) {
        return testLeasingApplication().id(new ApplicationId(UUID.randomUUID())).status(status).build();
    }
}
