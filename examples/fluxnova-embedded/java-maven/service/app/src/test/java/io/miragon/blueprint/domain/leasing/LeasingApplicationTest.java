package io.miragon.blueprint.domain.leasing;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.OrderId;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LeasingApplicationTest {

    @Test
    @DisplayName("exposes the applicant's age and monthly net income")
    void exposesTheApplicantsAgeAndMonthlyNetIncome() {
        // given: an application built with a specific age and income
        LeasingApplication application = testLeasingApplication().age(40).monthlyNetIncome(4200.0).build();
        // then: both fields are exposed unchanged
        assertThat(application.age()).isEqualTo(40);
        assertThat(application.monthlyNetIncome()).isEqualTo(4200.0);
    }

    @Test
    @DisplayName("documentOrder attaches the order id and moves to ORDERED")
    void documentOrderAttachesTheOrderIdAndMovesToOrdered() {
        // given: a received application
        LeasingApplication application = testLeasingApplication().status(LeasingStatus.RECEIVED).build();
        // when: a bike order is attached
        LeasingApplication ordered = application.documentOrder(new OrderId("ORDER-1"));
        // then: the order id is set and the status is ORDERED
        assertThat(ordered).isEqualTo(
                testLeasingApplication().orderId(new OrderId("ORDER-1")).status(LeasingStatus.ORDERED).build());
    }

    @Test
    @DisplayName("selectAlternative swaps in the newly chosen bike")
    void selectAlternativeSwapsInTheNewlyChosenBike() {
        // given: an application whose requested bike was unavailable
        LeasingApplication application = testLeasingApplication().bikeId(new BikeId("BIKE-900")).build();
        // when: the customer accepts an alternative bike
        LeasingApplication updated = application.selectAlternative(new BikeId("BIKE-ALT"));
        // then: the chosen bike is recorded
        assertThat(updated.bikeId()).isEqualTo(new BikeId("BIKE-ALT"));
    }

    @Test
    @DisplayName("withContract records the issued contract")
    void withContractRecordsTheIssuedContract() {
        // given: an application without a contract yet
        LeasingApplication application = testLeasingApplication().build();
        // when: the contract system issues a contract
        LeasingApplication updated = application.withContract(new ContractId("CONTRACT-1"));
        // then: the contract id is recorded
        assertThat(updated.contractId()).isEqualTo(new ContractId("CONTRACT-1"));
    }

    @Test
    @DisplayName("reject changes the status to REJECTED")
    void rejectChangesTheStatusToRejected() {
        // given: a received application
        LeasingApplication application = testLeasingApplication().build();
        // when: it is rejected
        LeasingApplication rejected = application.reject();
        // then: the status is REJECTED
        assertThat(rejected.status()).isEqualTo(LeasingStatus.REJECTED);
    }

    @Test
    @DisplayName("withdraw moves the application to WITHDRAWN")
    void withdrawMovesTheApplicationToWithdrawn() {
        // given: a handed-over application
        LeasingApplication application = testLeasingApplication().status(LeasingStatus.HANDED_OVER).build();
        // when: the customer withdraws
        LeasingApplication withdrawn = application.withdraw();
        // then: the status is WITHDRAWN
        assertThat(withdrawn.status()).isEqualTo(LeasingStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("reportHandover moves the application to HANDED_OVER")
    void reportHandoverMovesTheApplicationToHandedOver() {
        // given: an ordered application
        LeasingApplication application = testLeasingApplication().status(LeasingStatus.ORDERED).build();
        // when: the handover is reported
        LeasingApplication handedOver = application.reportHandover();
        // then: the status is HANDED_OVER
        assertThat(handedOver.status()).isEqualTo(LeasingStatus.HANDED_OVER);
    }

    @Test
    @DisplayName("activate moves the application to ACTIVE")
    void activateMovesTheApplicationToActive() {
        // given: a handed-over application
        LeasingApplication application = testLeasingApplication().status(LeasingStatus.HANDED_OVER).build();
        // when: the leasing is activated
        LeasingApplication active = application.activate();
        // then: the status is ACTIVE
        assertThat(active.status()).isEqualTo(LeasingStatus.ACTIVE);
    }

    @Test
    @DisplayName("receive rejects an application whose monthly net income is zero")
    void receiveRejectsAnApplicationWhoseMonthlyNetIncomeIsZero() {
        // when / then: an application without income cannot be received
        assertThatThrownBy(() -> receiveApplication(0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Monthly net income must be greater than zero");
    }

    @Test
    @DisplayName("receive accepts the smallest positive monthly net income as RECEIVED")
    void receiveAcceptsTheSmallestPositiveMonthlyNetIncomeAsReceived() {
        // when: an application with a minimal income is received
        LeasingApplication application = receiveApplication(0.01);
        // then: it starts its lifecycle as RECEIVED
        assertThat(application.status()).isEqualTo(LeasingStatus.RECEIVED);
        assertThat(application.monthlyNetIncome()).isEqualTo(0.01);
    }

    @Test
    @DisplayName("receive creates a RECEIVED application without order and contract")
    void receiveCreatesAReceivedApplicationWithoutOrderAndContract() {
        // given: the data of a freshly submitted leasing request
        ApplicationId id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        // when: the application is received
        LeasingApplication received = LeasingApplication.receive(
                id,
                new CustomerName("John Doe"),
                new Email("john.doe@test.com"),
                35,
                3500.0,
                new BikeId("BIKE-900"),
                createdAt);
        // then: it is RECEIVED, carries the submitted data and has neither order nor contract yet
        assertThat(received).isEqualTo(testLeasingApplication().id(id).createdAt(createdAt).build());
    }

    @Test
    @DisplayName("cancel moves the application to CANCELLED")
    void cancelMovesTheApplicationToCancelled() {
        // given: a withdrawn application
        LeasingApplication application = testLeasingApplication().status(LeasingStatus.WITHDRAWN).build();
        // when: the compensation has completed
        LeasingApplication cancelled = application.cancel();
        // then: the status is CANCELLED
        assertThat(cancelled).isEqualTo(testLeasingApplication().status(LeasingStatus.CANCELLED).build());
    }

    private LeasingApplication receiveApplication(double monthlyNetIncome) {
        return LeasingApplication.receive(
                ApplicationId.newId(),
                new CustomerName("John Doe"),
                new Email("john.doe@test.com"),
                35,
                monthlyNetIncome,
                new BikeId("BIKE-900"),
                LocalDateTime.parse("2024-01-15T10:30:00"));
    }
}
