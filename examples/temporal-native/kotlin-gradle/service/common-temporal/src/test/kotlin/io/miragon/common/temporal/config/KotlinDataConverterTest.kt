package io.miragon.common.temporal.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Optional

class KotlinDataConverterTest {

    private data class Decision(val found: Boolean, val bikeId: String? = null)

    @Test
    fun `round-trips a Kotlin data class with a defaulted field`() {
        // given
        val converter = kotlinDataConverter()
        val payloads = converter.toPayload(Decision(found = true))

        // when
        val decision = converter.fromPayload(payloads.get(), Decision::class.java, Decision::class.java)

        // then
        assertThat(Optional.of(decision)).contains(Decision(found = true))
    }
}
