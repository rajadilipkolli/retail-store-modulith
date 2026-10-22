/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders.internal.persistence;

import com.example.retail.orders.internal.domain.OrderIdempotency;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderIdempotencyRepository extends JpaRepository<OrderIdempotency, Long> {

    Optional<OrderIdempotency> findByCustomerIdAndIdempotencyKey(
            Long customerId, String idempotencyKey);

    Optional<OrderIdempotency> findByOrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from OrderIdempotency i where i.id = :id")
    Optional<OrderIdempotency> findLockedById(@Param("id") Long id);

    @Modifying(clearAutomatically = true)
    @Query(
            """
            update OrderIdempotency i set i.leaseExpiresAt = :expiresAt
            where i.id = :id and i.status = :status and i.claimOwner = :owner
              and i.leaseExpiresAt > :now
            """)
    int renewLease(
            Long id, String status, String owner, LocalDateTime now, LocalDateTime expiresAt);

    @Modifying(clearAutomatically = true)
    @Query(
            """
            update OrderIdempotency i set i.claimOwner = null, i.leaseExpiresAt = null
            where i.id = :id and i.status = :status and i.claimOwner = :owner
            """)
    int releaseLease(Long id, String status, String owner);

    @Modifying(clearAutomatically = true)
    @Query(
            """
            update OrderIdempotency i
            set i.status = :newStatus, i.responsePayload = :responsePayload,
                i.lastModifiedDate = :now, i.claimOwner = null, i.leaseExpiresAt = null
            where i.id = :id and i.status = :status and i.claimOwner = :owner
              and i.leaseExpiresAt > :now
            """)
    int completeClaim(
            Long id,
            String status,
            String owner,
            LocalDateTime now,
            String newStatus,
            String responsePayload);

    List<OrderIdempotency> findByStatusIn(List<String> statuses);
}
