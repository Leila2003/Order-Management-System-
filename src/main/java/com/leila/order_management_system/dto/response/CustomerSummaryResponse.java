package com.leila.order_management_system.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Aggregate spending summary for a single customer")
public record CustomerSummaryResponse(

        @Schema(description = "Customer id", example = "1")
        Long customerId,

        @Schema(description = "Number of non-cancelled orders placed", example = "12")
        long orderCount,

        @Schema(description = "Sum of totalAmount across all non-cancelled orders", example = "1543.50")
        BigDecimal totalSpend,

        @Schema(description = "Timestamp of the customer's most recent order, or null if they have none")
        Instant lastOrderDate
) {
}
