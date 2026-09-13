package com.leila.order_management_system.dto.response;

import com.leila.order_management_system.model.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


public record OrderSummaryResponse(
        UUID id,
        UUID customerId,
        String customerName,
        String status,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getCustomer().getId(),
                order.getCustomer().getName(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
