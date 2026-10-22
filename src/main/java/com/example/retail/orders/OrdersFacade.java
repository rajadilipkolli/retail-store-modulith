/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders;

import com.example.retail.shared.models.PagedResult;
import com.example.retail.shared.orders.OrderRequest;
import com.example.retail.shared.orders.OrderResponse;
import java.util.List;
import java.util.Optional;

/**
 * Public API facade for the Orders module.
 *
 * <p>Orchestrates cross-module order lifecycle and saga workflows using the public APIs of Catalog,
 * Inventory, and Payments.
 */
public interface OrdersFacade {

    /**
     * Creates an order using catalog prices, reserves inventory and USD payment, then confirms
     * payment and consumes inventory. Publishes confirmation and order events on success.
     *
     * <p>Keys are scoped to the customer. A matching completed request replays its stored response,
     * including the original price snapshot. Reservation failures trigger a compensation attempt
     * before the original error is rethrown; unfinished processing is retained for recovery.
     *
     * @param request the order creation request
     * @param idempotencyKey trimmed retry key; null or blank generates a new key
     * @throws IllegalArgumentException if required order data is missing, a quantity is
     *     nonpositive, the customer or a product does not exist, or a reservation returns an
     *     unsuccessful status
     * @throws com.example.retail.orders.internal.domain.IdempotencyConflictException if the key
     *     belongs to a different request, its claim is unfinished, or processing ownership is lost
     * @throws RuntimeException if a downstream operation fails; compensation failures may be
     *     attached as suppressed exceptions
     * @throws NullPointerException if an item has a null product code
     * @throws tools.jackson.core.JacksonException if a stored response cannot be read or a new
     *     response cannot be serialized
     * @return the created or replayed order response
     */
    OrderResponse createOrder(OrderRequest request, String idempotencyKey);

    /** Returns the order response matching the supplied identifier, or empty if not found. */
    Optional<OrderResponse> findOrderById(Long id);

    /**
     * Returns a sorted page of orders with pagination metadata.
     *
     * @param pageNo zero-based page index
     * @param pageSize maximum orders per page; must be positive
     * @param sortDir ascending for "asc" (case-insensitive), descending otherwise
     * @throws IllegalArgumentException if pagination is invalid or the sort property is null or
     *     blank
     */
    PagedResult<OrderResponse> findAllOrders(
            int pageNo, int pageSize, String sortBy, String sortDir);

    /**
     * Returns a page of the customer's orders sorted by ascending ID.
     *
     * @param pageNo zero-based page index
     * @param pageSize maximum orders per page; must be positive
     * @throws IllegalArgumentException if the page index is negative or page size is not positive
     */
    PagedResult<OrderResponse> findOrdersByCustomerId(Long customerId, int pageNo, int pageSize);

    /**
     * Searches source, delivery address fields, and product codes, with optional customer and
     * status filters. Keyword matching ignores case and uses SQL LIKE patterns. Similarity matching
     * uses PostgreSQL trigram operators and scores, ordered by descending best score and then ID;
     * keyword results are ordered by ascending ID.
     *
     * @param mode "similarity" (case-insensitive) selects trigram matching; otherwise keyword
     *     matching
     * @param status exact order status name, or null to omit the filter
     * @param threshold score cutoff for explicit similarity comparisons, defaulting to 0.3; matches
     *     accepted by PostgreSQL's trigram operator are also included regardless of this cutoff
     * @param pageNo zero-based page index
     * @param pageSize maximum orders per page; must be positive
     * @throws IllegalArgumentException if pagination is invalid or the status name is unknown
     */
    PagedResult<OrderResponse> searchOrders(
            String term,
            String mode,
            Long customerId,
            String status,
            Double threshold,
            int pageNo,
            int pageSize);

    /**
     * Replaces the delivery address when nonnull and updates the modification time. Customer,
     * items, and prices are preserved.
     *
     * @return the saved order details
     * @throws com.example.retail.orders.internal.domain.OrderNotFoundException if the order is
     *     absent
     * @throws NullPointerException if the request is null
     */
    OrderResponse updateOrder(Long id, OrderRequest request);

    /** Deletes an order by ID without compensating payment or inventory. */
    void deleteOrderById(Long id);

    /**
     * Records cancellation intent, refunds confirmed payment or releases reserved payment, and
     * restores reserved or consumed inventory before marking the order {@code ROLLBACK} and
     * publishing an order event. Repeated cancellation of a rolled-back order has no further
     * business effects. The original creation response remains available for idempotent replay.
     *
     * @throws IllegalArgumentException if the order has no recoverable reservation references
     * @throws com.example.retail.orders.internal.domain.OrderNotFoundException if the referenced
     *     order is absent
     * @throws com.example.retail.orders.internal.domain.IdempotencyConflictException if another
     *     worker owns an active claim or processing ownership is lost
     * @throws IllegalStateException if a confirmed order has no payment or inventory reservation
     * @throws RuntimeException if compensation fails; cancellation intent remains for recovery
     */
    void cancelOrder(Long id);

    /**
     * Creates each order independently with a fresh retry key, preserving request order. Runtime
     * failures become {@code SERVICE_UNAVAILABLE} responses with null order IDs and do not stop
     * later requests.
     *
     * @return one response per request, or an empty list for null or empty input
     */
    List<OrderResponse> saveBatchOrders(List<OrderRequest> requests);
}
