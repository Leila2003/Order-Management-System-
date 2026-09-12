package com.leila.order_management_system.controller;

import com.leila.order_management_system.dto.request.CreateOrderRequest;
import com.leila.order_management_system.dto.request.UpdateOrderStatusRequest;
import com.leila.order_management_system.dto.response.OrderResponse;
import com.leila.order_management_system.dto.response.OrderSummaryResponse;
import com.leila.order_management_system.dto.response.PageResponse;
import com.leila.order_management_system.model.OrderStatus;
import com.leila.order_management_system.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Create, list, and update orders")
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/api/orders")
    @Operation(summary = "List orders", description = """
            Paginated, server-side filtered order list. All filters are optional
            and combine with AND. Dates are plain calendar dates (yyyy-MM-dd) and
            are treated as an inclusive-from / exclusive-to UTC day range.
            """)
    public PageResponse<OrderSummaryResponse> listOrders(
            @Parameter(description = "Filter by order status") @RequestParam(required = false) String status,
            @Parameter(description = "Filter by customer id") @RequestParam(required = false) Long customerId,
            @Parameter(description = "Only orders created on/after this date") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Only orders created before this date") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(hidden = true) Pageable pageable
    ) {
        OrderStatus statusFilter = parseStatusOrNull(status);
        Instant fromInstant = from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return orderService.listOrders(statusFilter, customerId, fromInstant, toInstant, pageable);
    }

    @GetMapping("/api/orders/{id}")
    @Operation(summary = "Get a single order with its line items")
    public OrderResponse getOrder(@PathVariable Long id) {
        return orderService.getOrder(id);
    }

    @PostMapping("/api/orders")
    @Operation(summary = "Create an order", description = """
            Deducts stock for every line item atomically. If any item does not
            have enough stock, the whole order is rejected and nothing is
            deducted (409 Conflict).
            """)
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/api/orders/{id}/status")
    @Operation(summary = "Update an order's status", description = """
            Only forward transitions defined by the order lifecycle are
            accepted (see OrderStatus): PENDING -> PROCESSING/CANCELLED ->
            SHIPPED -> DELIVERED. DELIVERED and CANCELLED are terminal.
            Cancelling restocks the order's items.
            """)
    public OrderResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderService.updateStatus(id, request);
    }

    private OrderStatus parseStatusOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid status '%s'. Allowed values: %s".formatted(raw, Arrays.toString(OrderStatus.values())));
        }
    }
}
