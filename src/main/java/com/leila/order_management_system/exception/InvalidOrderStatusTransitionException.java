package com.leila.order_management_system.exception;

/** Thrown when a status update would move an order along a disallowed transition. Mapped to HTTP 409. */
public class InvalidOrderStatusTransitionException extends RuntimeException {
    public InvalidOrderStatusTransitionException(String message) {
        super(message);
    }
}
