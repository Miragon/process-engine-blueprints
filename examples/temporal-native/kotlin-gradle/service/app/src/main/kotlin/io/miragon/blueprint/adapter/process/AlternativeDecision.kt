package io.miragon.blueprint.adapter.process

data class AlternativeDecision(
    val alternativeFound: Boolean,
    val bikeId: String? = null,
)
