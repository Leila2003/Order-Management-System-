package com.leila.order_management_system.service.impl;

import com.leila.order_management_system.dto.request.CreateOrderRequest;
import com.leila.order_management_system.dto.request.OrderItemRequest;
import com.leila.order_management_system.dto.request.UpdateOrderStatusRequest;
import com.leila.order_management_system.dto.response.OrderResponse;
import com.leila.order_management_system.dto.response.OrderSummaryResponse;
import com.leila.order_management_system.dto.response.PageResponse;
import com.leila.order_management_system.exception.InsufficientStockException;
import com.leila.order_management_system.exception.InvalidOrderStatusTransitionException;
import com.leila.order_management_system.exception.ResourceNotFoundException;
import com.leila.order_management_system.model.Customer;
import com.leila.order_management_system.model.Order;
import com.leila.order_management_system.model.OrderItem;
import com.leila.order_management_system.model.OrderStatus;
import com.leila.order_management_system.model.Product;
import com.leila.order_management_system.repository.CustomerRepository;
import com.leila.order_management_system.repository.OrderRepository;
import com.leila.order_management_system.repository.ProductRepository;
import com.leila.order_management_system.service.OrderService;
import com.leila.order_management_system.specification.OrderSpecifications;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listOrders(OrderStatus status, Long customerId, Instant from, Instant to,
                                                          Pageable pageable) {
        
        Specification<Order> spec = Specification
                .where(OrderSpecifications.hasStatus(status))
                .and(OrderSpecifications.hasCustomerId(customerId))
                .and(OrderSpecifications.createdFrom(from))
                .and(OrderSpecifications.createdBefore(to));

        Page<Order> page = orderRepository.findAll(spec, pageable);
        return PageResponse.of(page, OrderSummaryResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id) {
        Order order = findOrderOrThrow(id);
        return OrderResponse.from(order);
    }

    /**
     * Creates an order and deducts stock atomically.
     * <p>
     * Concurrency strategy: PESSIMISTIC locking via "SELECT ... FOR UPDATE"
     * ({@link ProductRepository#findByIdForUpdate}). The whole method runs in
     * one transaction, so the lock on each product row is held until commit.
     * If two requests race for the same low-stock product, the second one
     * blocks at the SELECT FOR UPDATE until the first transaction commits
     * (deducting the stock) or rolls back; it then re-reads the *updated*
     * stock_quantity and correctly fails with {@link InsufficientStockException}
     * if there isn't enough left. Two concurrent requests can never both
     * succeed in overselling the same unit.
     * <p>
     * Trade-off (see DESIGN.md for the full discussion): this serializes
     * writers on a given product, which is the right choice for a
     * correctness-critical, occasional-write path like checkout. An
     * optimistic-locking alternative (a {@code @Version} column, retry on
     * conflict) would scale writes better under high contention but adds
     * retry complexity and can reject legitimate concurrent requests.
     */
    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer %d not found".formatted(request.customerId())));

        // Lock products in a fixed order (ascending id) so that two orders
        // sharing several products always acquire their row locks in the
        // same sequence - this rules out lock-ordering deadlocks.
        List<OrderItemRequest> sortedItems = request.items().stream()
                .sorted(Comparator.comparing(OrderItemRequest::productId))
                .toList();

        Order order = new Order();
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING);

        int total = 0;
        for (OrderItemRequest itemRequest : sortedItems) {
            Product product = lockProductOrThrow(itemRequest.productId());

            if (product.getStockQuantity() < itemRequest.quantity()) {
                throw new InsufficientStockException(
                        "Insufficient stock for product %d ('%s'): requested %d, available %d"
                                .formatted(product.getId(), product.getName(), itemRequest.quantity(), product.getStockQuantity()));
            }

            product.setStockQuantity(product.getStockQuantity() - itemRequest.quantity());
            productRepository.save(product);

            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(product);
            orderItem.setQuantity(itemRequest.quantity());
            orderItem.setUnitPrice(product.getUnitPrice()); // snapshot the price NOW, not a live reference
            order.addItem(orderItem);

            total += orderItem.subtotal();
        }

        order.setTotalAmount(BigDecimal.valueOf(total));
        Order saved = orderRepository.save(order);
        return OrderResponse.from(saved);
    }

    @Override
    @Transactional
    public OrderResponse updateStatus(Long id, UpdateOrderStatusRequest request) {
        Order order = findOrderOrThrow(id);
        OrderStatus newStatus = parseStatus(request.status());
        OrderStatus current = order.getStatus();

        if (!current.canTransitionTo(newStatus)) {
            throw new InvalidOrderStatusTransitionException(
                    "Cannot transition order %d from %s to %s".formatted(id, current, newStatus));
        }

        // Cancelling an order returns its items' quantities to stock.
        if (newStatus == OrderStatus.CANCELLED) {
            for (OrderItem item : order.getOrderItems()) {
                Product product = lockProductOrThrow(item.getProduct().getId());
                product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                productRepository.save(product);
            }
        }

        order.setStatus(newStatus);
        order.setUpdatedAt(Instant.now());
        Order saved = orderRepository.save(order);
        return OrderResponse.from(saved);
    }

    private Order findOrderOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order %d not found".formatted(id)));
    }

    private Product lockProductOrThrow(Long productId) {
        return productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product %d not found".formatted(productId)));
    }

    private OrderStatus parseStatus(String raw) {
        try {
            return OrderStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid status '%s'. Allowed values: %s".formatted(raw, Arrays.toString(OrderStatus.values())));
        }
    }
}
