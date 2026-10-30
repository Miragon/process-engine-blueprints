package io.miragon.blueprint.adapter.process

import io.temporal.activity.ActivityInterface

@ActivityInterface
interface RequestCancellation {
    fun requestCancellation(orderId: String): Boolean
}
