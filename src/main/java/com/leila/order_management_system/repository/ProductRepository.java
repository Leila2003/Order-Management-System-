package com.leila.order_management_system.repository;

import com.leila.order_management_system.model.Product;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Cheap, unlocked read used only to decide which stock-deduction
     * strategy {@code OrderServiceImpl#deductStockAndBuildItem} should take.
     * Returns a projection rather than a managed entity so this read never
     * enters the persistence context - see {@link ProductStockView}.
     */
    @Query("""
            select p.id as id, p.name as name, p.unitPrice as unitPrice, p.stockQuantity as stockQuantity
            from Product p
            where p.id = :id
            """)
    Optional<ProductStockView> findStockView(@Param("id") UUID id);

    /**
     * Atomic, lock-free stock deduction for the "plenty of stock" case: a
     * single conditional {@code UPDATE} that only succeeds if enough stock
     * is still available. Postgres takes a row-level write lock for the
     * duration of this statement regardless, so two concurrent calls on the
     * same row always serialise and the second one re-checks the {@code
     * WHERE} clause against the post-commit value - it can never oversell,
     * it just fails fast (0 rows updated) instead of blocking on an
     * up-front lock. See DESIGN.md for when this is used instead of {@link
     * #findByIdForUpdate}.
     *
     * @return the number of rows updated: 1 on success, 0 if there wasn't
     *         enough stock left at the moment the statement ran.
     */
    @Modifying
    @Query("update Product p set p.stockQuantity = p.stockQuantity - :quantity " +
            "where p.id = :id and p.stockQuantity >= :quantity")
    int deductStockIfAvailable(@Param("id") UUID id, @Param("quantity") int quantity);
}
