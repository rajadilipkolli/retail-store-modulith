/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.inventory.internal.persistence;

import com.example.retail.inventory.internal.domain.InventoryReservation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {

    Optional<InventoryReservation> findByIdempotencyKey(String idempotencyKey);

    List<InventoryReservation> findByOrderId(Long orderId);

    @Query(
            value =
                    """
            SELECT EXISTS (
                SELECT 1 FROM inventory.inventory_reservations r,
                    jsonb_array_elements(CAST(r.items_json AS jsonb)) item
                WHERE r.status = 'RESERVED' AND item ->> 'productCode' = :productCode
            )
            """,
            nativeQuery = true)
    boolean existsReservedByProductCode(@Param("productCode") String productCode);
}
