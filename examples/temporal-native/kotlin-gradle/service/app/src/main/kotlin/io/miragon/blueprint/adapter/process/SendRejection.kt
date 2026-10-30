package io.miragon.blueprint.adapter.process

import io.temporal.activity.ActivityInterface

@ActivityInterface
interface SendRejection {
    fun sendRejection(applicationId: String)
}
