package io.miragon.blueprint.adapter.process

private const val MINIMUM_AGE = 18
private const val MAXIMUM_AGE = 99
private const val MINIMUM_MONTHLY_NET_INCOME = 1500.0

fun isSolvent(age: Int, monthlyNetIncome: Double): Boolean = when {
    age < MINIMUM_AGE -> false
    monthlyNetIncome < MINIMUM_MONTHLY_NET_INCOME -> false
    age > MAXIMUM_AGE -> false
    else -> true
}
