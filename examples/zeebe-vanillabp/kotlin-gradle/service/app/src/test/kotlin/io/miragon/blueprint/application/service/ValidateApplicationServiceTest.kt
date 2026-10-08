package io.miragon.blueprint.application.service

import io.miragon.blueprint.domain.leasing.ApplicationInvalidException
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ValidateApplicationServiceTest {

    private val underTest = ValidateApplicationService()

    @Test
    fun `validate accepts a well-formed application`() {

        // given: a valid, solvent application
        val application = testLeasingApplication()

        // when / then: validation passes
        assertThatCode { underTest.validate(application) }.doesNotThrowAnyException()
    }

    @Test
    fun `validate rejects an application without income`() {

        // given: an application with zero monthly net income
        val application = testLeasingApplication(monthlyNetIncome = 0.0)

        // when / then: validation surfaces the application as invalid
        assertThatThrownBy { underTest.validate(application) }
            .isInstanceOf(ApplicationInvalidException::class.java)
    }
}
