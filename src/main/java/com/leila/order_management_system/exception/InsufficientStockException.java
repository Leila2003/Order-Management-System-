package com.leila.order_management_system.exception;

/** Thrown when an order requests more units of a product than are in stock. Mapped to HTTP 409. */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
