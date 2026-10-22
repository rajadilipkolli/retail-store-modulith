/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.inventory.internal.web;

import com.example.retail.inventory.internal.application.InventoryService;
import com.example.retail.shared.inventory.InventoryRequest;
import com.example.retail.shared.inventory.InventoryReservationRequest;
import com.example.retail.shared.inventory.InventoryReservationResult;
import com.example.retail.shared.inventory.InventoryResponse;
import com.example.retail.shared.models.PagedResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
@Validated
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public PagedResult<InventoryResponse> getAllInventories(
            @RequestParam(defaultValue = "0", required = false) int pageNo,
            @RequestParam(defaultValue = "10", required = false) int pageSize,
            @RequestParam(defaultValue = "id", required = false) String sortBy,
            @RequestParam(defaultValue = "asc", required = false) String sortDir) {
        return inventoryService.findAllInventories(pageNo, pageSize, sortBy, sortDir);
    }

    @GetMapping("/{productCode}")
    public ResponseEntity<InventoryResponse> getInventoryByProductCode(
            @PathVariable String productCode,
            @RequestParam(required = false) @Min(0) @Max(5) Integer delay) {
        if (delay != null && delay > 0) {
            try {
                TimeUnit.SECONDS.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return inventoryService
                .findInventoryByProductCode(productCode)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/product")
    public ResponseEntity<PagedResult<InventoryResponse>> getInventoryByProductCodes(
            @RequestParam List<String> codes,
            @RequestParam(defaultValue = "0", required = false) int pageNo,
            @RequestParam(defaultValue = "10", required = false) int pageSize,
            @RequestParam(defaultValue = "id", required = false) String sortBy,
            @RequestParam(defaultValue = "asc", required = false) String sortDir) {
        return ResponseEntity.ok(
                inventoryService.getInventoryByProductCodes(
                        codes, pageNo, pageSize, sortBy, sortDir));
    }

    @PostMapping("/generate")
    public boolean updateInventoryWithRandomValue(
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestParam(required = false) @Min(1) @Max(10000) Integer batchSize) {
        inventoryService.updateGeneratedInventory(idempotencyKey, batchSize);
        return true;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<InventoryResponse> createInventory(
            @RequestBody @Valid InventoryRequest inventoryRequest) {
        InventoryResponse response = inventoryService.saveInventory(inventoryRequest);
        return ResponseEntity.created(URI.create("/api/v1/inventory/" + response.productCode()))
                .body(response);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InventoryResponse> updateInventory(
            @PathVariable Long id, @RequestBody @Valid InventoryRequest inventoryRequest) {
        return inventoryService
                .updateInventoryById(id, inventoryRequest)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping(value = "/product/{productCode}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InventoryResponse> updateInventoryByProductCode(
            @PathVariable String productCode,
            @RequestBody @Valid InventoryRequest inventoryRequest) {
        if (inventoryRequest.productCode() != null
                && !inventoryRequest.productCode().equals(productCode)) {
            return ResponseEntity.badRequest().build();
        }
        return inventoryService
                .updateInventoryByProductCode(productCode, inventoryRequest)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<InventoryResponse> deleteInventory(@PathVariable Long id) {
        return inventoryService
                .findInventoryById(id)
                .map(
                        inventory -> {
                            inventoryService.deleteInventoryById(id);
                            return ResponseEntity.ok(inventory);
                        })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/reserve")
    public ResponseEntity<InventoryReservationResult> reserve(
            @RequestBody @Valid InventoryReservationRequest request) {
        InventoryReservationResult result = inventoryService.reserve(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/release/{idempotencyKey}")
    public ResponseEntity<InventoryReservationResult> release(@PathVariable String idempotencyKey) {
        return ResponseEntity.ok(inventoryService.release(idempotencyKey));
    }

    @PostMapping("/confirm/{idempotencyKey}")
    public ResponseEntity<InventoryReservationResult> confirm(@PathVariable String idempotencyKey) {
        return ResponseEntity.ok(inventoryService.consume(idempotencyKey));
    }

    @GetMapping("/reservation/{idempotencyKey}")
    public ResponseEntity<InventoryReservationResult> getReservationStatus(
            @PathVariable String idempotencyKey) {
        return inventoryService
                .getReservationStatus(idempotencyKey)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
