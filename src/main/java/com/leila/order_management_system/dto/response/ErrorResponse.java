package com.leila.order_management_system.dto.response;

import java.time.Instant;
import java.util.List;


public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldErrorResponse> fieldErrors
) {
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, List.of());
    }

    public static ErrorResponse ofValidation(int status, String error, String message, String path,
                                              List<FieldErrorResponse> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, error, message, path, fieldErrors);
    }
}
