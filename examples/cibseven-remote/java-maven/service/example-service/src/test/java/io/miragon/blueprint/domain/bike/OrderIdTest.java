package io.miragon.blueprint.domain.bike;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderIdTest {

    @Test
    @DisplayName("exposes the wrapped order id")
    void exposesTheWrappedOrderId() {
        // given/when: an order id is created from a non-blank value
        OrderId orderId = new OrderId("ORDER-1");
        // then: the raw value is exposed unchanged
        assertThat(orderId.value()).isEqualTo("ORDER-1");
    }

    @Test
    @DisplayName("rejects a blank order id")
    void rejectsABlankOrderId() {
        // when/then: a blank value is refused
        assertThatThrownBy(() -> new OrderId("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects an order id made of no-break spaces")
    void rejectsAnOrderIdOfNoBreakSpaces() {
        // when/then: no-break spaces count as blank, too
        assertThatThrownBy(() -> new OrderId("\u00A0\u2007\u202F"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
