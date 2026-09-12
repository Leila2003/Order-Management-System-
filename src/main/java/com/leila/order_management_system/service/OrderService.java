package com.leila.order_management_system.service;

import com.leila.order_management_system.dto.request.CreateOrderRequest;
import com.leila.order_management_system.dto.request.UpdateOrderStatusRequest;
import com.leila.order_management_system.dto.response.OrderResponse;
import com.leila.order_management_system.dto.response.OrderSummaryResponse;
import com.leila.order_management_system.dto.response.PageResponse;
import com.leila.order_management_system.model.OrderStatus;
import java.time.Instant;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    PageResponse<OrderSummaryResponse> listOrders(OrderStatus status, Long customerId, Instant from, Instant to,
                                                   Pageable pageable);

    OrderResponse getOrder(Long id);

    OrderResponse createOrder(CreateOrderRequest request);

    OrderResponse updateStatus(Long id, UpdateOrderStatusRequest request);
}
