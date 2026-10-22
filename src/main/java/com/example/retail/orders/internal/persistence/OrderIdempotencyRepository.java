/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders.internal.persistence;

import com.example.retail.orders.internal.domain.OrderIdempotency;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderIdempotencyRepository extends JpaRepository<OrderIdempotency, Long> {

    Optional<OrderIdempotency> findByCustomerIdAndIdempotencyKey(
            Long customerId, String idempotencyKey);

    Optional<OrderIdempotency> findByOrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from OrderIdempotency i where i.id = :id")
    Optional<OrderIdempotency> findLockedById(@Param("id") Long id);

    List<OrderIdempotency> findByStatusIn(List<String> statuses);
}
