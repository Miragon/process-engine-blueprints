package io.miragon.blueprint.adapter.process

import io.temporal.activity.ActivityInterface

@ActivityInterface
interface OrderBike {
    fun orderBike(applicationId: String, bikeId: String): String
}
