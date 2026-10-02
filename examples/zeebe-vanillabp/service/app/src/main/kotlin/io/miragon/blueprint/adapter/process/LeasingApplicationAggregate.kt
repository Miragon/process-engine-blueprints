package io.miragon.blueprint.adapter.process

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.leasing.ContractId
import io.miragon.blueprint.domain.leasing.CustomerName
import io.miragon.blueprint.domain.leasing.Email
import io.miragon.blueprint.domain.leasing.LeasingApplication
import io.miragon.blueprint.domain.leasing.LeasingStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import java.time.LocalDateTime

@Entity(name = "leasing_application")
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.NONE,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE,
)
@JsonInclude(JsonInclude.Include.NON_NULL)
class LeasingApplicationAggregate(

    @Id
    @Column(name = "application_id", nullable = false)
    @get:JsonProperty
    val applicationId: String,

    @Column(name = "customer_name", nullable = false)
    val customerName: String,

    @Column(name = "email", nullable = false)
    val email: String,

    @Column(name = "age", nullable = false)
    @get:JsonProperty
    val age: Int,

    @Column(name = "monthly_net_income", nullable = false)
    @get:JsonProperty
    val monthlyNetIncome: Double,

    @Column(name = "bike_id", nullable = false)
    var bikeId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: LeasingStatus,

    @Column(name = "order_id")
    var orderId: String? = null,

    @Column(name = "contract_id")
    var contractId: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime,

    @Column(name = "clarify_alternative_task_id")
    var clarifyAlternativeTaskId: String? = null,

    @Column(name = "clarification_requested_at")
    var clarificationRequestedAt: LocalDateTime? = null,

    @Column(name = "alternative_found")
    @get:JsonProperty
    var alternativeFound: Boolean? = null,

    @Column(name = "cancellation_possible")
    @get:JsonProperty
    var cancellationPossible: Boolean? = null,
) {

    @get:JsonProperty
    val bikeAvailable: Boolean
        get() = orderId != null

    fun toDomain(): LeasingApplication =
        LeasingApplication(
            id = ApplicationId.of(applicationId),
            customerName = CustomerName(customerName),
            email = Email(email),
            age = age,
            monthlyNetIncome = monthlyNetIncome,
            bikeId = BikeId(bikeId),
            status = status,
            createdAt = createdAt,
            orderId = orderId?.let { OrderId(it) },
            contractId = contractId?.let { ContractId(it) },
        )

    fun adopt(application: LeasingApplication) {
        require(application.id.value.toString() == applicationId) {
            "Application ${application.id.value} does not belong to workflow aggregate $applicationId"
        }
        bikeId = application.bikeId.value
        status = application.status
        orderId = application.orderId?.value
        contractId = application.contractId?.value
    }

    fun placedOrderId(): OrderId =
        OrderId(checkNotNull(orderId) { "No bike order placed for application $applicationId" })

    fun clarificationRequested(taskId: String, requestedAt: LocalDateTime) {
        clarifyAlternativeTaskId = taskId
        clarificationRequestedAt = requestedAt
        alternativeFound = null
    }

    fun clarificationCancelled() {
        clarifyAlternativeTaskId = null
        clarificationRequestedAt = null
    }

    fun resolveClarification(alternativeFound: Boolean): String {
        val taskId = clarifyAlternativeTaskId
            ?: throw NoSuchElementException("No open alternative clarification for application $applicationId")
        this.alternativeFound = alternativeFound
        clarifyAlternativeTaskId = null
        clarificationRequestedAt = null
        return taskId
    }

    companion object {

        fun of(application: LeasingApplication): LeasingApplicationAggregate =
            LeasingApplicationAggregate(
                applicationId = application.id.value.toString(),
                customerName = application.customerName.value,
                email = application.email.value,
                age = application.age,
                monthlyNetIncome = application.monthlyNetIncome,
                bikeId = application.bikeId.value,
                status = application.status,
                orderId = application.orderId?.value,
                contractId = application.contractId?.value,
                createdAt = application.createdAt,
            )
    }
}
