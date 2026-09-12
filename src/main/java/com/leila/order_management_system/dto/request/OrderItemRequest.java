package com.leila.order_management_system.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(

        @Schema(description = "Id of the product being ordered", example = "1")
        @NotNull(message = "productId is required")
        Long productId,

        @Schema(description = "Number of units of this product", example = "2")
        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        Integer quantity
) {
}
