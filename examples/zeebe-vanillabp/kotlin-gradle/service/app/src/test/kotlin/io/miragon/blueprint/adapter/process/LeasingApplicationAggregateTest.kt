package io.miragon.blueprint.adapter.process

import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.leasing.ContractId
import io.miragon.blueprint.domain.leasing.LeasingStatus
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.util.UUID

class LeasingApplicationAggregateTest {

    @Nested
    inner class Mapping {

        @Test
        fun `of copies every field including the nullable order and contract ids`() {
            // given: a fully populated application carrying an order and a contract
            val application = testLeasingApplication(
                status = LeasingStatus.ACTIVE,
                orderId = OrderId("ORDER-1"),
                contractId = ContractId("CONTRACT-1"),
            )

            // when: it becomes a workflow aggregate
            val aggregate = LeasingApplicationAggregate.of(application)

            // then: each field is carried over unchanged
            assertThat(aggregate.applicationId).isEqualTo(application.id.value.toString())
            assertThat(aggregate.customerName).isEqualTo(application.customerName.value)
            assertThat(aggregate.email).isEqualTo(application.email.value)
            assertThat(aggregate.age).isEqualTo(application.age)
            assertThat(aggregate.monthlyNetIncome).isEqualTo(application.monthlyNetIncome)
            assertThat(aggregate.bikeId).isEqualTo(application.bikeId.value)
            assertThat(aggregate.status).isEqualTo(application.status)
            assertThat(aggregate.createdAt).isEqualTo(application.createdAt)
            assertThat(aggregate.orderId).isEqualTo("ORDER-1")
            assertThat(aggregate.contractId).isEqualTo("CONTRACT-1")
        }

        @Test
        fun `toDomain reconstructs every field including the nullable order and contract ids`() {
            // given: an application carrying an order and a contract
            val application = testLeasingApplication(
                status = LeasingStatus.ACTIVE,
                orderId = OrderId("ORDER-1"),
                contractId = ContractId("CONTRACT-1"),
            )

            // when: it travels through the workflow aggregate and back
            val reconstructed = LeasingApplicationAggregate.of(application).toDomain()

            // then: each field is reconstructed unchanged
            assertThat(reconstructed).isEqualTo(application)
        }

        @Test
        fun `toDomain keeps the order and contract ids null when the aggregate has none`() {
            // given: an aggregate without an order or a contract
            val aggregate = LeasingApplicationAggregate.of(testLeasingApplication())

            // when: it is mapped back to the domain
            val application = aggregate.toDomain()

            // then: both optional references stay null
            assertThat(application.orderId).isNull()
            assertThat(application.contractId).isNull()
        }

        @Test
        fun `adopt takes over what the domain changed and keeps the state of the workflow`() {
            // given: an aggregate parked on an open clarification
            val application = testLeasingApplication()
            val aggregate = LeasingApplicationAggregate.of(application)
            aggregate.clarificationRequested("4711", LocalDateTime.of(2026, 3, 1, 8, 0))

            // when: the domain moved the application on
            aggregate.adopt(
                application
                    .selectAlternative(BikeId("BIKE-ALT"))
                    .withContract(ContractId("CONTRACT-1"))
                    .documentOrder(OrderId("ORDER-1")),
            )

            // then: the business state follows the domain while the open clarification survives
            assertThat(aggregate.bikeId).isEqualTo("BIKE-ALT")
            assertThat(aggregate.contractId).isEqualTo("CONTRACT-1")
            assertThat(aggregate.orderId).isEqualTo("ORDER-1")
            assertThat(aggregate.status).isEqualTo(LeasingStatus.ORDERED)
            assertThat(aggregate.clarifyAlternativeTaskId).isEqualTo("4711")
        }

        @Test
        fun `adopt takes over a withdrawn order and contract`() {
            // given: an aggregate carrying an order and a contract
            val application = testLeasingApplication(orderId = OrderId("ORDER-1"), contractId = ContractId("CONTRACT-1"))
            val aggregate = LeasingApplicationAggregate.of(application)

            // when: the domain no longer carries them
            aggregate.adopt(application.copy(orderId = null, contractId = null))

            // then: the aggregate follows
            assertThat(aggregate.orderId).isNull()
            assertThat(aggregate.contractId).isNull()
        }

        @Test
        fun `adopt refuses the application of another workflow`() {
            // given: an aggregate and the application of a different case
            val aggregate = LeasingApplicationAggregate.of(testLeasingApplication())
            val stranger = testLeasingApplication(id = ApplicationId(UUID.fromString("00000000-0000-0000-0000-000000000001")))

            // when / then: the aggregate keeps its identity
            assertThatThrownBy { aggregate.adopt(stranger) }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class BikeOrder {

        @Test
        fun `placedOrderId returns the recorded order`() {
            val aggregate = LeasingApplicationAggregate.of(testLeasingApplication(orderId = OrderId("ORDER-1")))

            assertThat(aggregate.placedOrderId()).isEqualTo(OrderId("ORDER-1"))
        }

        @Test
        fun `placedOrderId fails when no order was placed`() {
            val aggregate = LeasingApplicationAggregate.of(testLeasingApplication(orderId = null))

            assertThatThrownBy { aggregate.placedOrderId() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining(aggregate.applicationId)
        }
    }

    @Nested
    inner class AlternativeClarification {

        private val requestedAt = LocalDateTime.of(2026, 3, 1, 8, 0)

        @Test
        fun `a requested clarification remembers the user task and when it appeared`() {
            // given: an aggregate that already answered an earlier clarification
            val aggregate = LeasingApplicationAggregate.of(testLeasingApplication())
            aggregate.alternativeFound = true

            // when: the engine creates the user task again
            aggregate.clarificationRequested("4711", requestedAt)

            // then: the task is open and the earlier answer is forgotten
            assertThat(aggregate.clarifyAlternativeTaskId).isEqualTo("4711")
            assertThat(aggregate.clarificationRequestedAt).isEqualTo(requestedAt)
            assertThat(aggregate.alternativeFound).isNull()
        }

        @Test
        fun `resolving hands out the user task, records the decision and closes the clarification`() {
            // given: an open clarification
            val aggregate = LeasingApplicationAggregate.of(testLeasingApplication())
            aggregate.clarificationRequested("4711", requestedAt)

            // when: an alternative was found
            val taskId = aggregate.resolveClarification(alternativeFound = true)

            // then: the task to complete is returned and the clarification is closed
            assertThat(taskId).isEqualTo("4711")
            assertThat(aggregate.alternativeFound).isTrue()
            assertThat(aggregate.clarifyAlternativeTaskId).isNull()
            assertThat(aggregate.clarificationRequestedAt).isNull()
        }

        @Test
        fun `resolving records a declined alternative`() {
            // given: an open clarification
            val aggregate = LeasingApplicationAggregate.of(testLeasingApplication())
            aggregate.clarificationRequested("4711", requestedAt)

            // when: no alternative was found
            aggregate.resolveClarification(alternativeFound = false)

            // then: the decision is recorded for the gateway
            assertThat(aggregate.alternativeFound).isFalse()
        }

        @Test
        fun `resolving fails while no clarification is open`() {
            // given: an aggregate without an open clarification
            val aggregate = LeasingApplicationAggregate.of(testLeasingApplication())

            // when / then: there is nothing to resolve
            assertThatThrownBy { aggregate.resolveClarification(alternativeFound = true) }
                .isInstanceOf(NoSuchElementException::class.java)
                .hasMessageContaining(aggregate.applicationId)
            assertThat(aggregate.alternativeFound).isNull()
        }

        @Test
        fun `a cancelled clarification is closed without a decision`() {
            // given: an open clarification
            val aggregate = LeasingApplicationAggregate.of(testLeasingApplication())
            aggregate.clarificationRequested("4711", requestedAt)

            // when: the engine cancels the user task
            aggregate.clarificationCancelled()

            // then: nothing is open and no decision was taken
            assertThat(aggregate.clarifyAlternativeTaskId).isNull()
            assertThat(aggregate.clarificationRequestedAt).isNull()
            assertThat(aggregate.alternativeFound).isNull()
        }
    }

    @Nested
    inner class EngineVisibleState {

        private val engineJson = JsonMapper.builder().build()

        @Test
        fun `a fresh aggregate releases only its id and the decision inputs to the engine`() {
            // given: a freshly received application
            val aggregate = LeasingApplicationAggregate.of(testLeasingApplication(age = 35, monthlyNetIncome = 3500.0))

            // when: it is serialised the way the engine adapter sends it along
            val released = engineJson.readTree(engineJson.writeValueAsString(aggregate))

            // then: neither personal data nor business state beyond the routing values leaves the service
            assertThat(released.propertyNames())
                .containsExactlyInAnyOrder("applicationId", "age", "monthlyNetIncome")
            assertThat(released["applicationId"].asString()).isEqualTo(aggregate.applicationId)
            assertThat(released["age"].asInt()).isEqualTo(35)
            assertThat(released["monthlyNetIncome"].asDouble()).isEqualTo(3500.0)
        }

        @Test
        fun `the gateway decisions are released once they are taken`() {
            // given: an aggregate whose order is placed and whose clarification and cancellation are decided
            val aggregate = LeasingApplicationAggregate.of(
                testLeasingApplication(orderId = OrderId("ORDER-1"), contractId = ContractId("CONTRACT-1")),
            )
            aggregate.clarificationRequested("4711", LocalDateTime.of(2026, 3, 1, 8, 0))
            aggregate.resolveClarification(alternativeFound = true)
            aggregate.cancellationPossible = false

            // when: it is serialised the way the engine adapter sends it along
            val released = engineJson.readTree(engineJson.writeValueAsString(aggregate))

            // then: only the routing values are added
            assertThat(released.propertyNames()).containsExactlyInAnyOrder(
                "applicationId",
                "age",
                "monthlyNetIncome",
                "alternativeFound",
                "cancellationPossible",
            )
            assertThat(released["alternativeFound"].asBoolean()).isTrue()
            assertThat(released["cancellationPossible"].asBoolean()).isFalse()
        }
    }
}
