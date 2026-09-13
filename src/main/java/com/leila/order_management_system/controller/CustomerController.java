package com.leila.order_management_system.controller;

import com.leila.order_management_system.dto.response.CustomerSummaryResponse;
import com.leila.order_management_system.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Customer-level aggregates")
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/api/customers/{id}/summary")
    @Operation(summary = "Get a customer's order summary",
            description = "Total spend, order count, and last order date for the given customer.")
    public CustomerSummaryResponse getSummary(@PathVariable UUID id) {
        return customerService.getSummary(id);
    }
}
