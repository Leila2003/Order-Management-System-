package com.leila.order_management_system.service;

import com.leila.order_management_system.dto.response.CustomerSummaryResponse;

public interface CustomerService {

    CustomerSummaryResponse getSummary(Long customerId);
}
