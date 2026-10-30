package io.miragon.blueprint.adapter.process

import io.temporal.activity.ActivityInterface

@ActivityInterface
interface SendContract {
    fun sendContract(applicationId: String)
}
