/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.inventory.internal.application;

import com.example.retail.inventory.InventoryFacade;
import com.example.retail.shared.inventory.InventoryRequest;
import com.example.retail.shared.inventory.InventoryReservationRequest;
import com.example.retail.shared.inventory.InventoryReservationResult;
import com.example.retail.shared.inventory.InventoryResponse;
import com.example.retail.shared.models.PagedResult;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class InventoryFacadeImpl implements InventoryFacade {

    private final InventoryService inventoryService;

    public InventoryFacadeImpl(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Override
    public PagedResult<InventoryResponse> findAllInventories(
            int pageNo, int pageSize, String sortBy, String sortDir) {
        return inventoryService.findAllInventories(pageNo, pageSize, sortBy, sortDir);
    }

    @Override
    public Optional<InventoryResponse> findInventoryById(Long id) {
        return inventoryService.findInventoryById(id);
    }

    @Override
    public Optional<InventoryResponse> findInventoryByProductCode(String productCode) {
        return inventoryService.findInventoryByProductCode(productCode);
    }

    @Override
    public PagedResult<InventoryResponse> getInventoryByProductCodes(
            List<String> codes, int pageNo, int pageSize, String sortBy, String sortDir) {
        return inventoryService.getInventoryByProductCodes(
                codes, pageNo, pageSize, sortBy, sortDir);
    }

    @Override
    public InventoryResponse saveInventory(InventoryRequest inventoryRequest) {
        return inventoryService.saveInventory(inventoryRequest);
    }

    @Override
    public Optional<InventoryResponse> updateInventoryById(
            Long id, InventoryRequest inventoryRequest) {
        return inventoryService.updateInventoryById(id, inventoryRequest);
    }

    @Override
    public Optional<InventoryResponse> updateInventoryByProductCode(
            String productCode, InventoryRequest inventoryRequest) {
        return inventoryService.updateInventoryByProductCode(productCode, inventoryRequest);
    }

    @Override
    public void deleteInventoryById(Long id) {
        inventoryService.deleteInventoryById(id);
    }

    @Override
    public void updateGeneratedInventory(String idempotencyKey, Integer batchSize) {
        inventoryService.updateGeneratedInventory(idempotencyKey, batchSize);
    }

    @Override
    public void initializeProductInventory(String productCode) {
        inventoryService.initializeProductInventory(productCode);
    }

    @Override
    public InventoryReservationResult reserve(InventoryReservationRequest request) {
        return inventoryService.reserve(request);
    }

    @Override
    public InventoryReservationResult release(String idempotencyKey) {
        return inventoryService.release(idempotencyKey);
    }

    @Override
    public InventoryReservationResult cancelReservation(String idempotencyKey) {
        return inventoryService.cancelReservation(idempotencyKey);
    }

    @Override
    public InventoryReservationResult consume(String idempotencyKey) {
        return inventoryService.consume(idempotencyKey);
    }

    @Override
    public Optional<InventoryReservationResult> getReservationStatus(String idempotencyKey) {
        return inventoryService.getReservationStatus(idempotencyKey);
    }
}
