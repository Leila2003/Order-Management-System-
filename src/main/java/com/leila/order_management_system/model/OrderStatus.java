package com.leila.order_management_system.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of an order. The {@link #allowedNextStates()} map is the single
 * place that decides which transitions PUT /api/orders/{id}/status accepts -
 * see OrderServiceImpl for how it is enforced.
 */
public enum OrderStatus {
    PENDING,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED;

  
    public Set<OrderStatus> allowedNextStates() {
        return switch (this) {
            case PENDING -> EnumSet.of(PROCESSING, CANCELLED);
            case PROCESSING -> EnumSet.of(SHIPPED, CANCELLED);
            case SHIPPED -> EnumSet.of(DELIVERED, CANCELLED);
            case DELIVERED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canTransitionTo(OrderStatus target) {
        return allowedNextStates().contains(target);
    }
}
