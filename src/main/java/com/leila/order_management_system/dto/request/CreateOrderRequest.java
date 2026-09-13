package com.leila.order_management_system.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(

        @Schema(description = "Id of the customer placing the order", example = "550e8400-e29b-41d4-a716-446655440000")
        @NotNull(message = "customerId is required")
        UUID customerId,

        @Schema(description = "At least one line item is required")
        @NotEmpty(message = "items must contain at least one line item")
        @Valid
        List<OrderItemRequest> items
) {
}
