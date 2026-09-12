package com.leila.order_management_system.repository;

import com.leila.order_management_system.dto.response.CustomerSummaryResponse;
import com.leila.order_management_system.model.Order;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@link JpaSpecificationExecutor} gives us {@code findAll(Specification, Pageable)}
 * for free, which is how GET /api/orders applies its optional filters and
 * pagination entirely in SQL (see {@link com.leila.order_management_system.specification.OrderSpecifications}).
 */
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    
    @Query("""
            select new com.leila.order_management_system.dto.response.CustomerSummaryResponse(
                c.id, count(o.id), coalesce(sum(o.totalAmount), 0), max(o.createdAt))
            from Customer c
            left join Order o on o.customer = c and o.status <> 'CANCELLED'
            where c.id = :customerId
            group by c.id
            """)
    Optional<CustomerSummaryResponse> findCustomerSummary(@Param("customerId") Long customerId);
}
