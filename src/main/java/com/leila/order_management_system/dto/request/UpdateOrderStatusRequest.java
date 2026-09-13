package com.leila.order_management_system.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UpdateOrderStatusRequest(

       
        @Schema(description = "New order status", example = "PROCESSING",
                allowableValues = {"PENDING", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED"})
        @NotBlank(message = "status is required")
        String status
) {
}
