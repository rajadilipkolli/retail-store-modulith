/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders.internal.web;

import com.example.retail.orders.OrdersFacade;
import com.example.retail.orders.internal.application.OrderGeneratorService;
import com.example.retail.orders.internal.domain.OrderNotFoundException;
import com.example.retail.orders.internal.domain.OrderStatus;
import com.example.retail.orders.internal.stream.OrderStreamStoreService;
import com.example.retail.shared.models.PagedResult;
import com.example.retail.shared.orders.OrderDto;
import com.example.retail.shared.orders.OrderRequest;
import com.example.retail.shared.orders.OrderResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/orders", "/api/orders"})
@Validated
public class OrderController {

    private final OrdersFacade ordersFacade;
    private final OrderGeneratorService orderGeneratorService;
    private final OrderStreamStoreService orderStreamStoreService;

    public OrderController(
            OrdersFacade ordersFacade,
            OrderGeneratorService orderGeneratorService,
            OrderStreamStoreService orderStreamStoreService) {
        this.ordersFacade = ordersFacade;
        this.orderGeneratorService = orderGeneratorService;
        this.orderStreamStoreService = orderStreamStoreService;
    }

    /** Returns a sorted order page with a zero-based page index. */
    @GetMapping
    public PagedResult<OrderResponse> getAllOrders(
            @RequestParam(defaultValue = "0", required = false) int pageNo,
            @RequestParam(defaultValue = "10", required = false) int pageSize,
            @RequestParam(defaultValue = "id", required = false) String sortBy,
            @RequestParam(defaultValue = "asc", required = false) String sortDir) {
        return ordersFacade.findAllOrders(pageNo, pageSize, sortBy, sortDir);
    }

    /** Returns HTTP 200 with the order, or raises an order-not-found error handled as HTTP 404. */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        return ordersFacade
                .findOrderById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    /**
     * Creates or replays an order and returns HTTP 201 with its details and canonical Location.
     * Idempotency conflicts propagate as HTTP 409; invalid order arguments as HTTP 400.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody @Valid OrderRequest orderRequest) {
        OrderResponse orderResponse = ordersFacade.createOrder(orderRequest, idempotencyKey);
        return ResponseEntity.created(URI.create("/api/v1/orders/" + orderResponse.orderId()))
                .body(orderResponse);
    }

    /**
     * Updates the delivery address and returns HTTP 200 with the order; missing orders yield HTTP
     * 404.
     */
    @PutMapping("/{id}")
    public ResponseEntity<OrderResponse> updateOrder(
            @PathVariable Long id, @RequestBody @Valid OrderRequest orderRequest) {
        return ResponseEntity.ok(ordersFacade.updateOrder(id, orderRequest));
    }

    /** Deletes the order without compensating payment or inventory and returns HTTP 202. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        ordersFacade.deleteOrderById(id);
        return ResponseEntity.accepted().build();
    }

    /** Returns HTTP 200 with a zero-based page of customer orders sorted by ascending ID. */
    @GetMapping("/customer/{id}")
    public ResponseEntity<PagedResult<OrderResponse>> getOrdersByCustomerId(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0", required = false) int pageNo,
            @RequestParam(defaultValue = "10", required = false) int pageSize) {
        return ResponseEntity.ok(ordersFacade.findOrdersByCustomerId(id, pageNo, pageSize));
    }

    /**
     * Searches orders with optional customer and status filters and returns HTTP 200.
     *
     * @throws IllegalArgumentException if a supplied similarity threshold is below zero or above
     *     one
     */
    @GetMapping("/search")
    public ResponseEntity<PagedResult<OrderResponse>> searchOrders(
            @RequestParam @NotBlank String term,
            @RequestParam(defaultValue = "keyword", required = false) String mode,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Double threshold,
            @RequestParam(defaultValue = "0", required = false) int pageNo,
            @RequestParam(defaultValue = "10", required = false) int pageSize) {
        if ("similarity".equalsIgnoreCase(mode)
                && threshold != null
                && (threshold < 0.0 || threshold > 1.0)) {
            throw new IllegalArgumentException("Threshold must be between 0.0 and 1.0");
        }
        return ResponseEntity.ok(
                ordersFacade.searchOrders(
                        term,
                        mode,
                        customerId,
                        status != null ? status.name() : null,
                        threshold,
                        pageNo,
                        pageSize));
    }

    /**
     * Runs sample order generation synchronously and returns HTTP 200 with success true when the
     * call completes, including when the key is already completed or in progress.
     *
     * @param batchSize total orders to generate, from 1 through 10,000; null defaults to ten
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Boolean>> createMockOrders(
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestParam(required = false)
                    @Min(1)
                    @Max(OrderGeneratorService.MAX_GENERATION_BATCH_SIZE)
                    Integer batchSize) {
        orderGeneratorService.generateOrders(idempotencyKey, batchSize);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * Returns a database page of order DTOs in ascending ID order using a zero-based page index.
     */
    @GetMapping("/store")
    public List<OrderDto> allFromStore(
            @RequestParam(defaultValue = "0", required = false) int pageNo,
            @RequestParam(defaultValue = "10", required = false) int pageSize) {
        return orderStreamStoreService.getAllOrders(pageNo, pageSize);
    }

    /**
     * Returns HTTP 200 with a cached order or database fallback. Missing or invalid IDs raise an
     * order-not-found error handled as HTTP 404.
     */
    @GetMapping("/store/{id}")
    public ResponseEntity<OrderDto> getOrderFromStoreById(@PathVariable String id) {
        return orderStreamStoreService
                .getOrderFromStoreById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(
                        () -> new OrderNotFoundException("Order with id " + id + " not found"));
    }
}
