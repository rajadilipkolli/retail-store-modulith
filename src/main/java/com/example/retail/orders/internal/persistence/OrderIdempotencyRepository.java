/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders.internal.persistence;

import com.example.retail.orders.internal.domain.OrderIdempotency;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderIdempotencyRepository extends JpaRepository<OrderIdempotency, Long> {

    Optional<OrderIdempotency> findByCustomerIdAndIdempotencyKey(
            Long customerId, String idempotencyKey);
}
