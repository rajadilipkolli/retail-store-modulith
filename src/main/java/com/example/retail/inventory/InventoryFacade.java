/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.inventory;

import com.example.retail.shared.inventory.InventoryRequest;
import com.example.retail.shared.inventory.InventoryReservationRequest;
import com.example.retail.shared.inventory.InventoryReservationResult;
import com.example.retail.shared.inventory.InventoryResponse;
import com.example.retail.shared.models.PagedResult;
import java.util.List;
import java.util.Optional;

/** Public API facade for the Inventory module. */
public interface InventoryFacade {

    PagedResult<InventoryResponse> findAllInventories(
            int pageNo, int pageSize, String sortBy, String sortDir);

    Optional<InventoryResponse> findInventoryById(Long id);

    Optional<InventoryResponse> findInventoryByProductCode(String productCode);

    PagedResult<InventoryResponse> getInventoryByProductCodes(
            List<String> codes, int pageNo, int pageSize, String sortBy, String sortDir);

    InventoryResponse saveInventory(InventoryRequest inventoryRequest);

    Optional<InventoryResponse> updateInventoryById(Long id, InventoryRequest inventoryRequest);

    Optional<InventoryResponse> updateInventoryByProductCode(
            String productCode, InventoryRequest inventoryRequest);

    void deleteInventoryById(Long id);

    void updateGeneratedInventory(String idempotencyKey, Integer batchSize);

    void initializeProductInventory(String productCode);

    InventoryReservationResult reserve(InventoryReservationRequest request);

    InventoryReservationResult release(String idempotencyKey);

    InventoryReservationResult consume(String idempotencyKey);

    Optional<InventoryReservationResult> getReservationStatus(String idempotencyKey);
}
