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
     * Executes the order creation saga with scoped request idempotency and failure compensation.
     *
     * @param request the order creation request
     * @param idempotencyKey optional caller-provided key for retry idempotency
     * @return the created or replayed order response
     */
    OrderResponse createOrder(OrderRequest request, String idempotencyKey);

    /** Returns the order response matching the supplied identifier, or empty if not found. */
    Optional<OrderResponse> findOrderById(Long id);

    /** Returns a sorted page of orders with pagination metadata. */
    PagedResult<OrderResponse> findAllOrders(
            int pageNo, int pageSize, String sortBy, String sortDir);

    /** Returns a page of orders belonging to the specified customer. */
    PagedResult<OrderResponse> findOrdersByCustomerId(Long customerId, int pageNo, int pageSize);

    /** Searches orders by keyword or PostgreSQL trigram similarity. */
    PagedResult<OrderResponse> searchOrders(
            String term,
            String mode,
            Long customerId,
            String status,
            Double threshold,
            int pageNo,
            int pageSize);

    /** Updates an existing order. */
    OrderResponse updateOrder(Long id, OrderRequest request);

    /** Deletes an order by ID. */
    void deleteOrderById(Long id);

    /** Cancels an order and compensates payment and inventory. */
    void cancelOrder(Long id);

    /** Saves a batch of orders. */
    List<OrderResponse> saveBatchOrders(List<OrderRequest> requests);
}
