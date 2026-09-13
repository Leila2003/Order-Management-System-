package com.leila.order_management_system.service;

import com.leila.order_management_system.dto.response.CustomerSummaryResponse;
import java.util.UUID;

public interface CustomerService {

    CustomerSummaryResponse getSummary(UUID customerId);
}
