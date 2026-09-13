package com.leila.order_management_system.repository;

import java.util.UUID;

/**
 * Read-only projection used to decide which stock-deduction strategy to use
 * without loading a managed {@link com.leila.order_management_system.model.Product}
 * entity - see {@link ProductRepository#findStockView}. Being a plain
 * projection rather than an entity, it is never tracked by the persistence
 * context, so there is no risk of a stale in-memory value being flushed back
 * over a concurrent atomic {@code UPDATE}.
 */
public interface ProductStockView {
    UUID getId();

    String getName();

    Integer getUnitPrice();

    Integer getStockQuantity();
}
