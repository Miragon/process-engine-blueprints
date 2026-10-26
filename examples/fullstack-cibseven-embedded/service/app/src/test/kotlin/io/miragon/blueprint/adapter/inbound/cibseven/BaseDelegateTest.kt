package io.miragon.blueprint.adapter.inbound.cibseven

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.cibseven.bpm.engine.delegate.DelegateExecution
import org.junit.jupiter.api.Test

class BaseDelegateTest {

    private val execution = mockk<DelegateExecution>()

    @Test
    fun `rethrows what the task throws unchanged, so the engine handles it`() {
        val failure = IllegalStateException("dealer unreachable")
        val underTest = object : BaseDelegate() {
            override fun executeTask(execution: DelegateExecution) = throw failure
        }
        every { execution.currentActivityId } returns "serviceTask_orderBike"

        assertThatThrownBy { underTest.execute(execution) }.isSameAs(failure)
    }
}
