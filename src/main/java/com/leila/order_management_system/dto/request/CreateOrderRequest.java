package com.leila.order_management_system.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateOrderRequest(

        @Schema(description = "Id of the customer placing the order", example = "1")
        @NotNull(message = "customerId is required")
        Long customerId,

        @Schema(description = "At least one line item is required")
        @NotEmpty(message = "items must contain at least one line item")
        @Valid
        List<OrderItemRequest> items
) {
}
