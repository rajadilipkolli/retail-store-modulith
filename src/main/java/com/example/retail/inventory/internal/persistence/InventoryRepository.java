/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.inventory.internal.persistence;

import com.example.retail.inventory.internal.domain.Inventory;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductCode(String productCode);

    boolean existsByProductCode(String productCode);

    List<Inventory> findByProductCodeIn(List<String> productCodes);

    Page<Inventory> findByProductCodeIn(List<String> productCodes, Pageable pageable);

    /** Returns the matching inventory with a pessimistic write lock, or empty if absent. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.id = :id")
    Optional<Inventory> findWithLockById(@Param("id") Long id);

    /** Returns the matching inventory with a pessimistic write lock, or empty if absent. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.productCode = :productCode")
    Optional<Inventory> findWithLockByProductCode(@Param("productCode") String productCode);

    /**
     * Locks matching inventory for writing in ascending product-code order; missing codes are
     * omitted.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "SELECT i FROM Inventory i WHERE i.productCode IN :productCodes ORDER BY i.productCode ASC")
    List<Inventory> findWithLockByProductCodeIn(@Param("productCodes") List<String> productCodes);
}
