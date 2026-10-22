/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.payments.internal.persistence;

import com.example.retail.payments.internal.domain.PaymentOperation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentOperationRepository extends JpaRepository<PaymentOperation, Long> {

    Optional<PaymentOperation> findByIdempotencyKey(String idempotencyKey);

    List<PaymentOperation> findByOrderId(Long orderId);
}
