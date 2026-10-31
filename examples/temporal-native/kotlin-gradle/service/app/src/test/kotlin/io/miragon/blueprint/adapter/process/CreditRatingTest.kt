package io.miragon.blueprint.adapter.process

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CreditRatingTest {

    @ParameterizedTest(name = "age {0}, income {1} -> solvent {2}")
    @CsvSource(
        "17, 5000.0, false",
        "18, 1500.0, true",
        "18, 1499.99, false",
        "99, 1500.0, true",
        "100, 5000.0, false",
        "40, 1499.99, false",
        "40, 3000.0, true",
    )
    fun `applies the credit-rating rules`(age: Int, monthlyNetIncome: Double, solvent: Boolean) {
        assertThat(isSolvent(age, monthlyNetIncome)).isEqualTo(solvent)
    }
}
