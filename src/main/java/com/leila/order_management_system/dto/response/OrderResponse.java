package com.leila.order_management_system.dto.response;

import com.leila.order_management_system.model.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;


public record OrderResponse(
        UUID id,
        UUID customerId,
        String customerName,
        String status,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant updatedAt,
        List<OrderItemResponse> items
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomer().getId(),
                order.getCustomer().getName(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getOrderItems().stream().map(OrderItemResponse::from).toList()
        );
    }
}
