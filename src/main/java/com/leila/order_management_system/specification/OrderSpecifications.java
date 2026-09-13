package com.leila.order_management_system.specification;

import com.leila.order_management_system.model.Order;
import com.leila.order_management_system.model.OrderStatus;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Builds the WHERE clause for GET /api/orders from whichever query
 * parameters were actually supplied. Each method returns a {@code null}
 * predicate when its argument is null; Spring Data's {@link Specification}
 * combinators (and/or) silently skip null predicates, so callers can chain
 * all four unconditionally and only the supplied filters end up in the
 * generated SQL - everything still runs as one indexed query, never an
 * in-memory filter.
 */
public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Order> hasCustomerId(UUID customerId) {
        return (root, query, cb) -> customerId == null ? null : cb.equal(root.get("customer").get("id"), customerId);
    }

    public static Specification<Order> createdFrom(Instant from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Order> createdBefore(Instant to) {
        return (root, query, cb) -> to == null ? null : cb.lessThan(root.get("createdAt"), to);
    }
}
