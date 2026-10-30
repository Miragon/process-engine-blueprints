package io.miragon.blueprint.adapter.process

import io.temporal.activity.ActivityInterface

@ActivityInterface
interface SendCancellationConfirmation {
    fun sendCancellationConfirmation(applicationId: String)
}
