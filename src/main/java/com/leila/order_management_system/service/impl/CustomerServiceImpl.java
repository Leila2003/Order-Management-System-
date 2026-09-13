package com.leila.order_management_system.service.impl;

import com.leila.order_management_system.dto.response.CustomerSummaryResponse;
import com.leila.order_management_system.exception.ResourceNotFoundException;
import com.leila.order_management_system.repository.CustomerRepository;
import com.leila.order_management_system.repository.OrderRepository;
import com.leila.order_management_system.service.CustomerService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional(readOnly = true)
    public CustomerSummaryResponse getSummary(UUID customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer %s not found".formatted(customerId));
        }
        // Single aggregation query - see OrderRepository#findCustomerSummary.
        return orderRepository.findCustomerSummary(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer %s not found".formatted(customerId)));
    }
}
