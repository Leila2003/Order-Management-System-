package com.leila.order_management_system.exception;

/** Thrown when a requested entity (customer, product, order) does not exist. Mapped to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
