package io.miragon.blueprint.adapter.process

data class LeasingRequest(
    val applicationId: String,
    val bikeId: String,
    val monthlyNetIncome: Double,
    val age: Int,
)
