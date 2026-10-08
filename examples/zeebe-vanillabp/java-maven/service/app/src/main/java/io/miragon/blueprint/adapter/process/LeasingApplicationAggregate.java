package io.miragon.blueprint.adapter.process;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.ContractId;
import io.miragon.blueprint.domain.leasing.CustomerName;
import io.miragon.blueprint.domain.leasing.Email;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;

@Entity(name = "leasing_application")
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LeasingApplicationAggregate {

    @Id
    @Column(name = "application_id", nullable = false)
    private String applicationId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "age", nullable = false)
    private int age;

    @Column(name = "monthly_net_income", nullable = false)
    private double monthlyNetIncome;

    @Column(name = "bike_id", nullable = false)
    private String bikeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LeasingStatus status;

    @Column(name = "order_id")
    private String orderId;

    @Column(name = "contract_id")
    private String contractId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "clarify_alternative_task_id")
    private String clarifyAlternativeTaskId;

    @Column(name = "clarification_requested_at")
    private LocalDateTime clarificationRequestedAt;

    @Column(name = "alternative_found")
    private Boolean alternativeFound;

    @Column(name = "cancellation_possible")
    private Boolean cancellationPossible;

    protected LeasingApplicationAggregate() {
    }

    private LeasingApplicationAggregate(LeasingApplication application) {
        this.applicationId = application.id().value().toString();
        this.customerName = application.customerName().value();
        this.email = application.email().value();
        this.age = application.age();
        this.monthlyNetIncome = application.monthlyNetIncome();
        this.bikeId = application.bikeId().value();
        this.status = application.status();
        this.orderId = valueOf(application.orderId());
        this.contractId = valueOf(application.contractId());
        this.createdAt = application.createdAt();
    }

    public static LeasingApplicationAggregate of(LeasingApplication application) {
        return new LeasingApplicationAggregate(application);
    }

    public LeasingApplication toDomain() {
        return new LeasingApplication(
                ApplicationId.of(applicationId),
                new CustomerName(customerName),
                new Email(email),
                age,
                monthlyNetIncome,
                new BikeId(bikeId),
                status,
                createdAt,
                orderId != null ? new OrderId(orderId) : null,
                contractId != null ? new ContractId(contractId) : null);
    }

    public void adopt(LeasingApplication application) {
        if (!application.id().value().toString().equals(applicationId)) {
            throw new IllegalArgumentException(
                    "Application " + application.id().value()
                            + " does not belong to workflow aggregate " + applicationId);
        }
        bikeId = application.bikeId().value();
        status = application.status();
        orderId = valueOf(application.orderId());
        contractId = valueOf(application.contractId());
    }

    public OrderId placedOrderId() {
        if (orderId == null) {
            throw new IllegalStateException("No bike order placed for application " + applicationId);
        }
        return new OrderId(orderId);
    }

    public void clarificationRequested(String taskId, LocalDateTime requestedAt) {
        clarifyAlternativeTaskId = taskId;
        clarificationRequestedAt = requestedAt;
        alternativeFound = null;
    }

    public void clarificationCancelled() {
        clarifyAlternativeTaskId = null;
        clarificationRequestedAt = null;
    }

    public String resolveClarification(boolean alternativeFound) {
        String taskId = clarifyAlternativeTaskId;
        if (taskId == null) {
            throw new NoSuchElementException("No open alternative clarification for application " + applicationId);
        }
        this.alternativeFound = alternativeFound;
        clarifyAlternativeTaskId = null;
        clarificationRequestedAt = null;
        return taskId;
    }

    @JsonProperty
    public String getApplicationId() {
        return applicationId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getEmail() {
        return email;
    }

    @JsonProperty
    public int getAge() {
        return age;
    }

    @JsonProperty
    public double getMonthlyNetIncome() {
        return monthlyNetIncome;
    }

    public String getBikeId() {
        return bikeId;
    }

    public LeasingStatus getStatus() {
        return status;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getContractId() {
        return contractId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getClarifyAlternativeTaskId() {
        return clarifyAlternativeTaskId;
    }

    public LocalDateTime getClarificationRequestedAt() {
        return clarificationRequestedAt;
    }

    @JsonProperty
    public Boolean getAlternativeFound() {
        return alternativeFound;
    }

    @JsonProperty
    public Boolean getCancellationPossible() {
        return cancellationPossible;
    }

    public void setCancellationPossible(Boolean cancellationPossible) {
        this.cancellationPossible = cancellationPossible;
    }

    @JsonProperty
    public boolean isBikeAvailable() {
        return orderId != null;
    }

    private static String valueOf(OrderId orderId) {
        return orderId != null ? orderId.value() : null;
    }

    private static String valueOf(ContractId contractId) {
        return contractId != null ? contractId.value() : null;
    }
}
