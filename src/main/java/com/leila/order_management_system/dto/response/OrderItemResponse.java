package com.leila.order_management_system.dto.response;

import com.leila.order_management_system.model.OrderItem;
import java.util.UUID;

public record OrderItemResponse(
        UUID productId,
        String productName,
        Integer quantity,
        Integer unitPrice,
        Integer subtotal
) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.subtotal()
        );
    }
}
