package io.miragon.blueprint.adapter.process;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.ContractId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class LeasingApplicationAggregateTest {

    @Nested
    class Mapping {

        @Test
        void ofCopiesEveryFieldIncludingTheNullableOrderAndContractIds() {
            // given: a fully populated application carrying an order and a contract
            LeasingApplication application = testLeasingApplication()
                    .status(LeasingStatus.ACTIVE)
                    .orderId(new OrderId("ORDER-1"))
                    .contractId(new ContractId("CONTRACT-1"))
                    .build();

            // when: it becomes a workflow aggregate
            LeasingApplicationAggregate aggregate = LeasingApplicationAggregate.of(application);

            // then: each field is carried over unchanged
            assertThat(aggregate.getApplicationId()).isEqualTo(application.id().value().toString());
            assertThat(aggregate.getCustomerName()).isEqualTo(application.customerName().value());
            assertThat(aggregate.getEmail()).isEqualTo(application.email().value());
            assertThat(aggregate.getAge()).isEqualTo(application.age());
            assertThat(aggregate.getMonthlyNetIncome()).isEqualTo(application.monthlyNetIncome());
            assertThat(aggregate.getBikeId()).isEqualTo(application.bikeId().value());
            assertThat(aggregate.getStatus()).isEqualTo(application.status());
            assertThat(aggregate.getCreatedAt()).isEqualTo(application.createdAt());
            assertThat(aggregate.getOrderId()).isEqualTo("ORDER-1");
            assertThat(aggregate.getContractId()).isEqualTo("CONTRACT-1");
        }

        @Test
        void toDomainReconstructsEveryFieldIncludingTheNullableOrderAndContractIds() {
            // given: an application carrying an order and a contract
            LeasingApplication application = testLeasingApplication()
                    .status(LeasingStatus.ACTIVE)
                    .orderId(new OrderId("ORDER-1"))
                    .contractId(new ContractId("CONTRACT-1"))
                    .build();

            // when: it travels through the workflow aggregate and back
            LeasingApplication reconstructed = LeasingApplicationAggregate.of(application).toDomain();

            // then: each field is reconstructed unchanged
            assertThat(reconstructed).isEqualTo(application);
        }

        @Test
        void toDomainKeepsTheOrderAndContractIdsNullWhenTheAggregateHasNone() {
            // given: an aggregate without an order or a contract
            LeasingApplicationAggregate aggregate = LeasingApplicationAggregate.of(testLeasingApplication().build());

            // when: it is mapped back to the domain
            LeasingApplication application = aggregate.toDomain();

            // then: both optional references stay null
            assertThat(application.orderId()).isNull();
            assertThat(application.contractId()).isNull();
        }

        @Test
        void adoptTakesOverWhatTheDomainChangedAndKeepsTheStateOfTheWorkflow() {
            // given: an aggregate parked on an open clarification
            LeasingApplication application = testLeasingApplication().build();
            LeasingApplicationAggregate aggregate = LeasingApplicationAggregate.of(application);
            aggregate.clarificationRequested("4711", LocalDateTime.of(2026, 3, 1, 8, 0));

            // when: the domain moved the application on
            aggregate.adopt(
                    application
                            .selectAlternative(new BikeId("BIKE-ALT"))
                            .withContract(new ContractId("CONTRACT-1"))
                            .documentOrder(new OrderId("ORDER-1")));

            // then: the business state follows the domain while the open clarification survives
            assertThat(aggregate.getBikeId()).isEqualTo("BIKE-ALT");
            assertThat(aggregate.getContractId()).isEqualTo("CONTRACT-1");
            assertThat(aggregate.getOrderId()).isEqualTo("ORDER-1");
            assertThat(aggregate.getStatus()).isEqualTo(LeasingStatus.ORDERED);
            assertThat(aggregate.getClarifyAlternativeTaskId()).isEqualTo("4711");
        }

        @Test
        void adoptTakesOverAWithdrawnOrderAndContract() {
            // given: an aggregate carrying an order and a contract
            LeasingApplication application = testLeasingApplication()
                    .orderId(new OrderId("ORDER-1"))
                    .contractId(new ContractId("CONTRACT-1"))
                    .build();
            LeasingApplicationAggregate aggregate = LeasingApplicationAggregate.of(application);

            // when: the domain no longer carries them
            aggregate.adopt(testLeasingApplication().build());

            // then: the aggregate follows
            assertThat(aggregate.getOrderId()).isNull();
            assertThat(aggregate.getContractId()).isNull();
        }

        @Test
        void adoptRefusesTheApplicationOfAnotherWorkflow() {
            // given: an aggregate and the application of a different case
            LeasingApplicationAggregate aggregate = LeasingApplicationAggregate.of(testLeasingApplication().build());
            LeasingApplication stranger = testLeasingApplication()
                    .id(new ApplicationId(UUID.fromString("00000000-0000-0000-0000-000000000001")))
                    .build();

            // when / then: the aggregate keeps its identity
            assertThatThrownBy(() -> aggregate.adopt(stranger)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class BikeOrder {

        @Test
        void placedOrderIdReturnsTheRecordedOrder() {
            LeasingApplicationAggregate aggregate = LeasingApplicationAggregate.of(orderedApplication());

            assertThat(aggregate.placedOrderId()).isEqualTo(new OrderId("ORDER-1"));
        }

        @Test
        void placedOrderIdFailsWhenNoOrderWasPlaced() {
            LeasingApplicationAggregate aggregate =
                    LeasingApplicationAggregate.of(testLeasingApplication().orderId(null).build());

            assertThatThrownBy(aggregate::placedOrderId)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(aggregate.getApplicationId());
        }

        private LeasingApplication orderedApplication() {
            return testLeasingApplication().orderId(new OrderId("ORDER-1")).build();
        }
    }

    @Nested
    class AlternativeClarification {

        private final LocalDateTime requestedAt = LocalDateTime.of(2026, 3, 1, 8, 0);

        @Test
        void aRequestedClarificationRemembersTheUserTaskAndWhenItAppeared() {
            // given: an aggregate that already answered an earlier clarification
            LeasingApplicationAggregate aggregate = LeasingApplicationAggregate.of(testLeasingApplication().build());
            aggregate.clarificationRequested("4710", requestedAt.minusDays(1));
            aggregate.resolveClarification(true);

            // when: the engine creates the user task again
            aggregate.clarificationRequested("4711", requestedAt);

            // then: the task is open and the earlier answer is forgotten
            assertThat(aggregate.getClarifyAlternativeTaskId()).isEqualTo("4711");
            assertThat(aggregate.getClarificationRequestedAt()).isEqualTo(requestedAt);
            assertThat(aggregate.getAlternativeFound()).isNull();
        }

        @Test
        void resolvingHandsOutTheUserTaskRecordsTheDecisionAndClosesTheClarification() {
            // given: an open clarification
            LeasingApplicationAggregate aggregate = LeasingApplicationAggregate.of(testLeasingApplication().build());
            aggregate.clarificationRequested("4711", requestedAt);

            // when: an alternative was found
            String taskId = aggregate.resolveClarification(true);

            // then: the task to complete is returned and the clarification is closed
            assertThat(taskId).isEqualTo("4711");
            assertThat(aggregate.getAlternativeFound()).isTrue();
            assertThat(aggregate.getClarifyAlternativeTaskId()).isNull();
            assertThat(aggregate.getClarificationRequestedAt()).isNull();
        }

        @Test
        void resolvingRecordsADeclinedAlternative() {
            // given: an open clarification
            LeasingApplicationAggregate aggregate = LeasingApplicationAggregate.of(testLeasingApplication().build());
            aggregate.clarificationRequested("4711", requestedAt);

            // when: no alternative was found
            aggregate.resolveClarification(false);

            // then: the decision is recorded for the gateway
            assertThat(aggregate.getAlternativeFound()).isFalse();
        }

        @Test
        void resolvingFailsWhileNoClarificationIsOpen() {
            // given: an aggregate without an open clarification
            LeasingApplicationAggregate aggregate = LeasingApplicationAggregate.of(testLeasingApplication().build());

            // when / then: there is nothing to resolve
            assertThatThrownBy(() -> aggregate.resolveClarification(true))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining(aggregate.getApplicationId());
            assertThat(aggregate.getAlternativeFound()).isNull();
        }

        @Test
        void aCancelledClarificationIsClosedWithoutADecision() {
            // given: an open clarification
            LeasingApplicationAggregate aggregate = LeasingApplicationAggregate.of(testLeasingApplication().build());
            aggregate.clarificationRequested("4711", requestedAt);

            // when: the engine cancels the user task
            aggregate.clarificationCancelled();

            // then: nothing is open and no decision was taken
            assertThat(aggregate.getClarifyAlternativeTaskId()).isNull();
            assertThat(aggregate.getClarificationRequestedAt()).isNull();
            assertThat(aggregate.getAlternativeFound()).isNull();
        }
    }

    @Nested
    class EngineVisibleState {

        private final JsonMapper engineJson = JsonMapper.builder().build();

        @Test
        void aFreshAggregateReleasesOnlyItsIdAndTheDecisionInputsToTheEngine() {
            // given: a freshly received application
            LeasingApplicationAggregate aggregate =
                    LeasingApplicationAggregate.of(testLeasingApplication().age(35).monthlyNetIncome(3500.0).build());

            // when: it is serialised the way the engine adapter sends it along
            JsonNode released = engineJson.readTree(engineJson.writeValueAsString(aggregate));

            // then: neither personal data nor business state beyond the routing values leaves the service
            assertThat(released.propertyNames())
                    .containsExactlyInAnyOrder("applicationId", "age", "monthlyNetIncome");
            assertThat(released.get("applicationId").asString()).isEqualTo(aggregate.getApplicationId());
            assertThat(released.get("age").asInt()).isEqualTo(35);
            assertThat(released.get("monthlyNetIncome").asDouble()).isEqualTo(3500.0);
        }

        @Test
        void theGatewayDecisionsAreReleasedOnceTheyAreTaken() {
            // given: an aggregate whose order is placed and whose clarification and cancellation are decided
            LeasingApplicationAggregate aggregate = LeasingApplicationAggregate.of(
                    testLeasingApplication()
                            .orderId(new OrderId("ORDER-1"))
                            .contractId(new ContractId("CONTRACT-1"))
                            .build());
            aggregate.clarificationRequested("4711", LocalDateTime.of(2026, 3, 1, 8, 0));
            aggregate.resolveClarification(true);
            aggregate.setCancellationPossible(false);

            // when: it is serialised the way the engine adapter sends it along
            JsonNode released = engineJson.readTree(engineJson.writeValueAsString(aggregate));

            // then: only the routing values are added
            assertThat(released.propertyNames()).containsExactlyInAnyOrder(
                    "applicationId",
                    "age",
                    "monthlyNetIncome",
                    "alternativeFound",
                    "cancellationPossible");
            assertThat(released.get("alternativeFound").asBoolean()).isTrue();
            assertThat(released.get("cancellationPossible").asBoolean()).isFalse();
        }
    }
}
