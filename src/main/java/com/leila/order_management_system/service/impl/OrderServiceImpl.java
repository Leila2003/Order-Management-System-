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
import com.leila.order_management_system.repository.ProductStockView;
import com.leila.order_management_system.service.OrderService;
import com.leila.order_management_system.specification.OrderSpecifications;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    /**
     * Stock level at/below which {@link #deductStockAndBuildItem} switches
     * from the lock-free atomic UPDATE to pessimistic locking. Matches the
     * "low stock" threshold already used by {@code idx_products_low_stock}
     * in schema.sql and Query 2, rather than introducing a second, unrelated
     * definition of "low stock".
     */
    private static final int PESSIMISTIC_LOCK_STOCK_THRESHOLD = 20;

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listOrders(OrderStatus status, UUID customerId, Instant from, Instant to,
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
    public OrderResponse getOrder(UUID id) {
        Order order = findOrderOrThrow(id);
        return OrderResponse.from(order);
    }

    /**
     * Creates an order and deducts stock atomically.
     * <p>
     * Concurrency strategy: a HYBRID of optimistic and pessimistic locking,
     * chosen per product based on how much stock is currently on hand (see
     * {@link #deductStockAndBuildItem}):
     * <ul>
     *   <li>Well-stocked products ({@code stock_quantity >
     *       PESSIMISTIC_LOCK_STOCK_THRESHOLD}) use a lock-free atomic
     *       {@code UPDATE ... WHERE stock_quantity >= :quantity}
     *       ({@link ProductRepository#deductStockIfAvailable}). This is the
     *       common case - most orders touch products nobody else is
     *       contending for - so it pays no locking cost at all.</li>
     *   <li>Low-stock products use PESSIMISTIC locking via
     *       "SELECT ... FOR UPDATE" ({@link ProductRepository#findByIdForUpdate}),
     *       exactly as before. This is where real contention concentrates
     *       (many buyers racing the last few units of a popular SKU), and
     *       serialising writers there is the right trade-off.</li>
     * </ul>
     * Either way, the whole method runs in one transaction, so any lock
     * taken (explicit, or implicit via the atomic UPDATE) is held until
     * commit. Two concurrent requests can never both succeed in overselling
     * the same unit - see DESIGN.md for the full discussion, including why
     * the initial (unlocked) stock-level read used to pick a strategy
     * doesn't need to be perfectly fresh for this to be correct.
     */
    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer %s not found".formatted(request.customerId())));

        // Process products in a fixed order (ascending id) so that two
        // orders sharing several products always acquire their row locks -
        // whether via SELECT FOR UPDATE or the atomic UPDATE's own implicit
        // row lock - in the same sequence. This rules out lock-ordering
        // deadlocks regardless of which strategy each item ends up using.
        List<OrderItemRequest> sortedItems = request.items().stream()
                .sorted(Comparator.comparing(OrderItemRequest::productId))
                .toList();

        Order order = new Order();
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING);

        int total = 0;
        for (OrderItemRequest itemRequest : sortedItems) {
            OrderItem orderItem = deductStockAndBuildItem(itemRequest.productId(), itemRequest.quantity());
            order.addItem(orderItem);
            total += orderItem.subtotal();
        }

        order.setTotalAmount(BigDecimal.valueOf(total));
        Order saved = orderRepository.save(order);
        return OrderResponse.from(saved);
    }

    /**
     * Deducts stock for one line item and returns the resulting (not yet
     * persisted) {@link OrderItem}. See {@link #createOrder} for which
     * locking strategy is picked and why.
     */
    private OrderItem deductStockAndBuildItem(UUID productId, int quantity) {
        ProductStockView stock = productRepository.findStockView(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product %s not found".formatted(productId)));

        OrderItem orderItem = new OrderItem();
        orderItem.setQuantity(quantity);

        if (stock.getStockQuantity() > PESSIMISTIC_LOCK_STOCK_THRESHOLD) {
            int updated = productRepository.deductStockIfAvailable(productId, quantity);
            if (updated == 0) {
                // Stock dropped below what was requested between the read
                // above and this UPDATE - a real "not enough stock right
                // now", not a transient conflict, so we fail fast rather
                // than retry.
                throw new InsufficientStockException(
                        "Insufficient stock for product %s ('%s'): requested %d"
                                .formatted(productId, stock.getName(), quantity));
            }
            // A lazy reference is enough to persist the FK - no need to load
            // the (now stale, in this in-memory view) full entity.
            orderItem.setProduct(productRepository.getReferenceById(productId));
            orderItem.setUnitPrice(stock.getUnitPrice()); // snapshot the price NOW, not a live reference
        } else {
            Product product = lockProductOrThrow(productId);
            if (product.getStockQuantity() < quantity) {
                throw new InsufficientStockException(
                        "Insufficient stock for product %s ('%s'): requested %d, available %d"
                                .formatted(product.getId(), product.getName(), quantity, product.getStockQuantity()));
            }
            product.setStockQuantity(product.getStockQuantity() - quantity);
            productRepository.save(product);

            orderItem.setProduct(product);
            orderItem.setUnitPrice(product.getUnitPrice()); // snapshot the price NOW, not a live reference
        }

        return orderItem;
    }

    @Override
    @Transactional
    public OrderResponse updateStatus(UUID id, UpdateOrderStatusRequest request) {
        Order order = findOrderOrThrow(id);
        OrderStatus newStatus = parseStatus(request.status());
        OrderStatus current = order.getStatus();

        if (!current.canTransitionTo(newStatus)) {
            throw new InvalidOrderStatusTransitionException(
                    "Cannot transition order %s from %s to %s".formatted(id, current, newStatus));
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

    private Order findOrderOrThrow(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order %s not found".formatted(id)));
    }

    private Product lockProductOrThrow(UUID productId) {
        return productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product %s not found".formatted(productId)));
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
