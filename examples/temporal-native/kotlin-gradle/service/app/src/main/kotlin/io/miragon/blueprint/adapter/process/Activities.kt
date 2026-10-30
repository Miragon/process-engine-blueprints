package io.miragon.blueprint.adapter.process

import io.temporal.activity.ActivityInterface

/** Every step the workflows run, in one place. One `*Activity` in `adapter/inbound/temporal` implements each. */
object Activities {

    @ActivityInterface
    interface SendContract {
        fun sendContract(applicationId: String)
    }

    @ActivityInterface
    interface SendReminderMail {
        fun sendReminderMail(applicationId: String)
    }

    @ActivityInterface
    interface SendRejection {
        fun sendRejection(applicationId: String)
    }

    @ActivityInterface
    interface OrderBike {
        fun orderBike(applicationId: String, bikeId: String): String
    }

    @ActivityInterface
    interface IssueInsurancePolicy {
        fun issueInsurancePolicy(applicationId: String)
    }

    @ActivityInterface
    interface ActivateLeasing {
        fun activateLeasing(applicationId: String)
    }

    @ActivityInterface
    interface CancelContract {
        fun cancelContract(applicationId: String)
    }

    @ActivityInterface
    interface CancelPolicy {
        fun cancelPolicy(applicationId: String)
    }

    @ActivityInterface
    interface SendCancellationConfirmation {
        fun sendCancellationConfirmation(applicationId: String)
    }

    @ActivityInterface
    interface RequestCancellation {
        fun requestCancellation(orderId: String): Boolean
    }

    @ActivityInterface
    interface BookCosts {
        fun bookCosts(orderId: String)
    }
}
