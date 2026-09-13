package com.leila.order_management_system.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Aggregate spending summary for a single customer")
public record CustomerSummaryResponse(

        @Schema(description = "Customer id", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID customerId,

        @Schema(description = "Number of non-cancelled orders placed", example = "12")
        Long orderCount,

        @Schema(description = "Sum of totalAmount across all non-cancelled orders", example = "1000.00")
        BigDecimal totalSpend,

        @Schema(description = "Timestamp of the customer's most recent order, or null if they have none")
        Instant lastOrderDate
) {
}
