package io.miragon.blueprint.adapter.process

import io.temporal.activity.ActivityInterface

/** Every step [CancelBikeOrderWorkflow] runs. One `*Activity` in `adapter/inbound/temporal` implements each. */
object CancelBikeOrderActivities {

    @ActivityInterface
    interface RequestCancellation {
        fun requestCancellation(orderId: String): Boolean
    }

    @ActivityInterface
    interface BookCosts {
        fun bookCosts(orderId: String)
    }
}
