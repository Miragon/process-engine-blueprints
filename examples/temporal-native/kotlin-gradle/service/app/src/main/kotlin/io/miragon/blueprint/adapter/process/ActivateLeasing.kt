package io.miragon.blueprint.adapter.process

import io.temporal.activity.ActivityInterface

@ActivityInterface
interface ActivateLeasing {
    fun activateLeasing(applicationId: String)
}
