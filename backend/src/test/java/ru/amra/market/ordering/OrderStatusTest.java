package ru.amra.market.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;

class OrderStatusTest {
    @Test
    void pendingCanBeConfirmedOrCancelled() {
        assertThat(OrderStatus.PENDING.transitionTo(OrderStatus.CONFIRMED)).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(OrderStatus.PENDING.transitionTo(OrderStatus.CANCELLED)).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void terminalStatesRejectEveryTransition() {
        for (var target : OrderStatus.values()) {
            assertThatIllegalStateException().isThrownBy(() -> OrderStatus.CONFIRMED.transitionTo(target));
            assertThatIllegalStateException().isThrownBy(() -> OrderStatus.CANCELLED.transitionTo(target));
        }
    }
}
