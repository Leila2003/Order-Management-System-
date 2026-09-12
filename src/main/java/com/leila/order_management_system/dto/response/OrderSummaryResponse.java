package com.leila.order_management_system.dto.response;

import com.leila.order_management_system.model.Order;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lightweight row used by GET /api/orders (the paginated list). It
 * deliberately omits line items: loading every item of every order on a
 * page would multiply the payload size and defeats the point of pagination
 * on a table that grows into the tens of millions of rows. Fetch
 * GET /api/orders/{id} for the full item breakdown.
 */
public record OrderSummaryResponse(
        Long id,
        Long customerId,
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
