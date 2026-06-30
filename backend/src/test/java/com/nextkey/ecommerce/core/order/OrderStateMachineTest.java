package com.nextkey.ecommerce.core.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * OrderStateMachine 狀態轉換規則單元測試。
 *
 * <p>重點：DEF-010 —— 轉換表與 canCancel() 一致性（SHIPPING 不可直接取消）。
 */
@DisplayName("OrderStateMachine 狀態轉換規則")
class OrderStateMachineTest {

    @Test
    @DisplayName("DEF-010: SHIPPING→CANCELLED 不允許（出貨後不可直接取消，須走退貨/退款）")
    void shippingToCancelled_isDenied() {
        assertThat(OrderStateMachine.canTransition("SHIPPING", "CANCELLED").isAllowed()).isFalse();
    }

    @Test
    @DisplayName("SHIPPING→DELIVERED 仍允許")
    void shippingToDelivered_isAllowed() {
        assertThat(OrderStateMachine.canTransition("SHIPPING", "DELIVERED").isAllowed()).isTrue();
    }

    @Test
    @DisplayName("DEF-010: getNextValidStates(SHIPPING) 只含 DELIVERED，不含 CANCELLED")
    void shippingNextStates_excludesCancelled() {
        assertThat(OrderStateMachine.getNextValidStates("SHIPPING"))
                .containsExactly("DELIVERED")
                .doesNotContain("CANCELLED");
    }

    @Test
    @DisplayName("canCancel 僅 CREATED/PAID/CONFIRMED")
    void canCancel_onlyEarlyStates() {
        assertThat(OrderStateMachine.canCancel("CREATED")).isTrue();
        assertThat(OrderStateMachine.canCancel("PAID")).isTrue();
        assertThat(OrderStateMachine.canCancel("CONFIRMED")).isTrue();
        assertThat(OrderStateMachine.canCancel("SHIPPING")).isFalse();
        assertThat(OrderStateMachine.canCancel("DELIVERED")).isFalse();
    }

    @Test
    @DisplayName("DEF-010 不變量：轉換表允許 X→CANCELLED 者，canCancel(X) 必為 true（兩層一致）")
    void transitionTableAndCanCancel_areConsistent() {
        List<String> allStatuses = List.of(
                "CREATED", "PAID", "CONFIRMED", "SHIPPING", "DELIVERED",
                "COMPLETED", "CANCELLED", "REFUNDING", "REFUNDED");
        for (String status : allStatuses) {
            boolean transitionAllowsCancel = OrderStateMachine.canTransition(status, "CANCELLED").isAllowed();
            if (transitionAllowsCancel) {
                assertThat(OrderStateMachine.canCancel(status))
                        .as("狀態 %s 轉換表允許 →CANCELLED，canCancel() 也必須為 true", status)
                        .isTrue();
            }
        }
    }
}
