package io.miragon.blueprint.adapter.process

import io.temporal.activity.ActivityInterface

@ActivityInterface
interface CancelContract {
    fun cancelContract(applicationId: String)
}
