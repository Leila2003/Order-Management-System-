package com.leila.order_management_system.dto.response;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * A small, explicit wrapper around Spring Data's {@link Page} so the JSON
 * shape returned to clients is stable and documented in Swagger, instead of
 * leaking Spring's internal Page/PageImpl serialization.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
