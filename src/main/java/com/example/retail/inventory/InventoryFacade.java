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

    /**
     * Returns a sorted page of inventory records with pagination metadata.
     *
     * @param pageNo zero-based page index
     * @param pageSize number of records per page, greater than zero
     * @param sortBy inventory property to sort by
     * @param sortDir {@code asc}, ignoring case, for ascending order; any other non-null value
     *     selects descending order
     * @throws IllegalArgumentException if the page index is negative, the page size is not
     *     positive, or the sort property is null or empty
     */
    PagedResult<InventoryResponse> findAllInventories(
            int pageNo, int pageSize, String sortBy, String sortDir);

    /** Returns the matching inventory, or an empty result when the ID is not found. */
    Optional<InventoryResponse> findInventoryById(Long id);

    /** Returns the matching inventory, or an empty result when the product code is not found. */
    Optional<InventoryResponse> findInventoryByProductCode(String productCode);

    /**
     * Returns a sorted page of inventory matching the supplied codes; missing codes are omitted.
     *
     * @param pageNo zero-based page index
     * @param pageSize number of records per page, greater than zero
     * @param sortBy inventory property to sort by
     * @param sortDir {@code asc}, ignoring case, for ascending order; any other non-null value
     *     selects descending order
     * @throws IllegalArgumentException if the page index is negative, the page size is not
     *     positive, or the sort property is null or empty
     */
    PagedResult<InventoryResponse> getInventoryByProductCodes(
            List<String> codes, int pageNo, int pageSize, String sortBy, String sortDir);

    /**
     * Creates inventory with the requested available quantity and no reserved units.
     *
     * @return the saved inventory, including its assigned ID
     * @throws com.example.retail.inventory.internal.domain.ProductAlreadyExistsException if
     *     inventory already exists for the product code
     */
    InventoryResponse saveInventory(InventoryRequest inventoryRequest);

    /**
     * Replaces the available quantity without changing the product code or reserved quantity.
     *
     * @return the updated inventory, or an empty result when the ID is not found
     * @throws com.example.retail.inventory.internal.domain.InventoryConflictException if the
     *     request specifies a different product code for an existing record
     */
    Optional<InventoryResponse> updateInventoryById(Long id, InventoryRequest inventoryRequest);

    /**
     * Replaces the available quantity for the supplied product code, preserving reserved units. The
     * product code in the request is ignored.
     *
     * @return the updated inventory, or an empty result when the supplied code is not found
     */
    Optional<InventoryResponse> updateInventoryByProductCode(
            String productCode, InventoryRequest inventoryRequest);

    /**
     * Deletes inventory if present; a missing ID has no effect.
     *
     * @throws com.example.retail.inventory.internal.domain.InventoryConflictException if the record
     *     has a positive reserved quantity or appears in a reservation with status {@code RESERVED}
     */
    void deleteInventoryById(Long id);

    /**
     * Replaces available quantities of existing generated records with random values from 1 through
     * 100, inclusive. No inventory records are created; reserved quantities are preserved.
     *
     * <p>Codes have the form {@code ProductCode_<key>_<index>}, with indices starting at zero and
     * ending before the effective batch size. A null or previously committed key has no effect. The
     * key and stock updates commit together, so a rolled-back attempt can be retried.
     *
     * @param idempotencyKey key identifying the generation batch
     * @param batchSize number of candidate codes, capped at 10,000; null or nonpositive values use
     *     101
     * @throws org.springframework.dao.DataAccessException if claiming the key or updating stock
     *     fails
     */
    void updateGeneratedInventory(String idempotencyKey, Integer batchSize);

    /** Creates inventory with zero available and reserved units if the product has no inventory. */
    void initializeProductInventory(String productCode);

    /**
     * Reserves stock using the total quantity requested for each product across all item lines. New
     * attempts persist a reservation and publish an inventory processed event. Missing products or
     * insufficient stock produce a {@code FAILED} result without changing stock quantities.
     *
     * <p>A matching retry returns the stored status and items without reserving stock or publishing
     * another event. Matching compares the order ID and item lines sorted by product code; the
     * relative order of lines for the same code still matters.
     *
     * @return the reservation outcome and items, including a stored failure on a matching retry
     * @throws com.example.retail.inventory.internal.domain.IdempotencyConflictException if the key
     *     already belongs to a different order or item list
     * @throws ArithmeticException if adding reserved units exceeds the integer range
     * @throws tools.jackson.core.JacksonException if request items cannot be serialized or stored
     *     items cannot be read
     */
    InventoryReservationResult reserve(InventoryReservationRequest request);

    /**
     * Marks a reserved reservation {@code RELEASED} and restores its units to available stock for
     * inventory records that still exist. Repeating a completed release returns the released result
     * without changing quantities again.
     *
     * @throws com.example.retail.inventory.internal.domain.InventoryNotFoundException if the key
     *     has no reservation
     * @throws com.example.retail.inventory.internal.domain.InvalidReservationTransitionException if
     *     the status is neither {@code RESERVED} nor {@code RELEASED}
     * @throws tools.jackson.core.JacksonException if stored items cannot be read
     */
    InventoryReservationResult release(String idempotencyKey);

    /**
     * Marks a reserved reservation {@code CONSUMED} and reduces reserved units for inventory
     * records that still exist, leaving available quantities unchanged. Repeating a completed
     * consumption returns the consumed result without changing quantities again.
     *
     * @throws com.example.retail.inventory.internal.domain.InventoryNotFoundException if the key
     *     has no reservation
     * @throws com.example.retail.inventory.internal.domain.InvalidReservationTransitionException if
     *     the status is neither {@code RESERVED} nor {@code CONSUMED}
     * @throws tools.jackson.core.JacksonException if stored items cannot be read
     */
    InventoryReservationResult consume(String idempotencyKey);

    /**
     * Releases reserved stock or restocks consumed stock once, keyed by the original reservation.
     * Repeated cancellation returns RELEASED without changing stock again. Restocking consumed
     * units leaves the reserved quantity unchanged.
     *
     * @throws com.example.retail.inventory.internal.domain.InventoryNotFoundException if the key
     *     has no reservation or an inventory record needed for restoration is missing
     * @throws com.example.retail.inventory.internal.domain.InvalidReservationTransitionException if
     *     the status is not RESERVED, CONSUMED, or RELEASED
     * @throws ArithmeticException if restoring available units exceeds the integer range
     * @throws tools.jackson.core.JacksonException if stored items cannot be read
     */
    InventoryReservationResult cancelReservation(String idempotencyKey);

    /**
     * Returns the stored status and items, or an empty result when the key has no reservation.
     *
     * @throws tools.jackson.core.JacksonException if stored items cannot be read
     */
    Optional<InventoryReservationResult> getReservationStatus(String idempotencyKey);
}
