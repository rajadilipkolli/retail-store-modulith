/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders.internal.application;

import com.example.retail.catalog.CatalogFacade;
import com.example.retail.inventory.InventoryFacade;
import com.example.retail.orders.OrdersFacade;
import com.example.retail.orders.internal.domain.IdempotencyConflictException;
import com.example.retail.orders.internal.domain.Order;
import com.example.retail.orders.internal.domain.OrderIdempotency;
import com.example.retail.orders.internal.domain.OrderItem;
import com.example.retail.orders.internal.domain.OrderNotFoundException;
import com.example.retail.orders.internal.domain.OrderStatus;
import com.example.retail.orders.internal.persistence.OrderIdempotencyRepository;
import com.example.retail.orders.internal.persistence.OrderRepository;
import com.example.retail.payments.PaymentsFacade;
import com.example.retail.shared.inventory.InventoryReservationRequest;
import com.example.retail.shared.inventory.InventoryReservationStatus;
import com.example.retail.shared.inventory.ReservationItem;
import com.example.retail.shared.models.PagedResult;
import com.example.retail.shared.orders.OrderConfirmedEvent;
import com.example.retail.shared.orders.OrderDto;
import com.example.retail.shared.orders.OrderItemRequest;
import com.example.retail.shared.orders.OrderItemResponse;
import com.example.retail.shared.orders.OrderRequest;
import com.example.retail.shared.orders.OrderResponse;
import com.example.retail.shared.payments.PaymentRefundRequest;
import com.example.retail.shared.payments.PaymentReservationRequest;
import com.example.retail.shared.payments.PaymentStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class OrdersFacadeImpl implements OrdersFacade {

    private static final Logger log = LoggerFactory.getLogger(OrdersFacadeImpl.class);

    private final OrderRepository orderRepository;
    private final OrderIdempotencyRepository idempotencyRepository;
    private final CatalogFacade catalogFacade;
    private final InventoryFacade inventoryFacade;
    private final PaymentsFacade paymentsFacade;
    private final OrderRequestFingerprinter fingerprinter;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;

    public OrdersFacadeImpl(
            OrderRepository orderRepository,
            OrderIdempotencyRepository idempotencyRepository,
            CatalogFacade catalogFacade,
            InventoryFacade inventoryFacade,
            PaymentsFacade paymentsFacade,
            OrderRequestFingerprinter fingerprinter,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.catalogFacade = catalogFacade;
        this.inventoryFacade = inventoryFacade;
        this.paymentsFacade = paymentsFacade;
        this.fingerprinter = fingerprinter;
        this.eventPublisher = eventPublisher;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.objectMapper = objectMapper;
    }

    @Override
    public OrderResponse createOrder(OrderRequest request, String idempotencyKey) {
        if (request == null
                || request.customerId() == null
                || request.customerId() <= 0
                || request.items() == null
                || request.items().isEmpty()
                || request.deliveryAddress() == null) {
            throw new IllegalArgumentException("Customer, items and delivery address are required");
        }
        String key =
                idempotencyKey == null || idempotencyKey.isBlank()
                        ? UUID.randomUUID().toString()
                        : idempotencyKey.trim();
        String fingerprint = fingerprinter.computeFingerprint(request);
        OrderIdempotency claim;
        try {
            claim = transactions.execute(tx -> claimOrder(request, key, fingerprint));
        } catch (DataIntegrityViolationException ex) {
            throw new IdempotencyConflictException(
                    "Order creation for key '" + key + "' is already in progress");
        }
        SagaResult result = process(claim.getId());
        if (result.failure() != null) {
            try {
                process(claim.getId()); // Compensation commits independently of the failed
                // reservation.
            } catch (RuntimeException compensationFailure) {
                result.failure().addSuppressed(compensationFailure);
            }
            throw result.failure();
        }
        return result.response();
    }

    private OrderIdempotency claimOrder(OrderRequest request, String key, String fingerprint) {
        var existing =
                idempotencyRepository.findByCustomerIdAndIdempotencyKey(request.customerId(), key);
        if (existing.isPresent()) {
            OrderIdempotency claim = existing.get();
            if (!fingerprint.equals(claim.getRequestFingerprint())) {
                throw new IdempotencyConflictException(
                        "Idempotency key was already used for a different order request");
            }
            if (!OrderIdempotency.STATUS_COMPLETED.equals(claim.getStatus())) {
                throw new IdempotencyConflictException(
                        "Order creation for key '" + key + "' is " + claim.getStatus());
            }
            return claim;
        }
        if (paymentsFacade.findCustomerById(request.customerId()).isEmpty()) {
            throw new IllegalArgumentException(
                    "Customer with id " + request.customerId() + " does not exist");
        }
        Order order =
                new Order()
                        .setCustomerId(request.customerId())
                        .setStatus(OrderStatus.NEW)
                        .setSource("WEB")
                        .setDeliveryAddress(request.deliveryAddress())
                        .setCreatedDate(LocalDateTime.now())
                        .setCreatedBy("system");
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest item : request.items()) {
            if (item == null || item.quantity() <= 0) {
                throw new IllegalArgumentException("Order quantities must be positive");
            }
            BigDecimal price =
                    catalogFacade
                            .findProductByCode(item.productCode())
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Product with code '"
                                                            + item.productCode()
                                                            + "' does not exist in catalog"))
                            .price();
            order.addOrderItem(
                    new OrderItem()
                            .setProductCode(item.productCode())
                            .setQuantity(item.quantity())
                            .setProductPrice(price));
            total = total.add(price.multiply(BigDecimal.valueOf(item.quantity())));
        }
        order.setTotalPrice(total.setScale(2, RoundingMode.HALF_UP));
        orderRepository.save(order);
        return idempotencyRepository.saveAndFlush(
                new OrderIdempotency()
                        .setCustomerId(request.customerId())
                        .setIdempotencyKey(key)
                        .setRequestFingerprint(fingerprint)
                        .setOrderId(order.getId())
                        .setOperationKey(operationKey(order.getId()))
                        .setStatus(OrderIdempotency.STATUS_PROCESSING)
                        .setCreatedDate(LocalDateTime.now()));
    }

    private record SagaResult(OrderResponse response, RuntimeException failure) {}

    private record ClaimWork(
            Long id,
            String status,
            String owner,
            String key,
            String responsePayload,
            Order order) {}

    private ClaimWork acquireClaim(Long claimId) {
        return transactions.execute(
                tx -> {
                    OrderIdempotency claim =
                            idempotencyRepository.findLockedById(claimId).orElseThrow();
                    Order order =
                            orderRepository
                                    .findOrderById(claim.getOrderId())
                                    .orElseThrow(
                                            () -> new OrderNotFoundException(claim.getOrderId()));
                    String owner = null;
                    if (!OrderIdempotency.STATUS_COMPLETED.equals(claim.getStatus())
                            && !OrderIdempotency.STATUS_FAILED.equals(claim.getStatus())) {
                        requireAvailableClaim(claim);
                        owner = UUID.randomUUID().toString();
                        claim.setClaimOwner(owner)
                                .setLeaseExpiresAt(LocalDateTime.now().plusMinutes(5));
                        idempotencyRepository.save(claim);
                    }
                    return new ClaimWork(
                            claimId,
                            claim.getStatus(),
                            owner,
                            claim.getOperationKey() != null
                                    ? claim.getOperationKey()
                                    : claim.getIdempotencyKey(),
                            claim.getResponsePayload(),
                            order);
                });
    }

    private static void requireAvailableClaim(OrderIdempotency claim) {
        if (claim.getClaimOwner() != null
                && claim.getLeaseExpiresAt() != null
                && claim.getLeaseExpiresAt().isAfter(LocalDateTime.now())) {
            throw new IdempotencyConflictException("Order processing is already in progress");
        }
    }

    private <T> T businessStep(ClaimWork work, Supplier<T> action) {
        LocalDateTime now = LocalDateTime.now();
        int renewed =
                transactions.execute(
                        tx ->
                                idempotencyRepository.renewLease(
                                        work.id(),
                                        work.status(),
                                        work.owner(),
                                        now,
                                        now.plusMinutes(5)));
        if (renewed != 1) {
            throw new IdempotencyConflictException("Order processing lease is no longer owned");
        }
        // The lease renewal commits before calling a module. No claim row lock is held here.
        return transactions.execute(step -> action.get());
    }

    private SagaResult process(Long claimId) {
        ClaimWork work = acquireClaim(claimId);
        if (work.owner() == null) {
            return new SagaResult(
                    OrderIdempotency.STATUS_COMPLETED.equals(work.status())
                                    && work.responsePayload() != null
                            ? objectMapper.readValue(work.responsePayload(), OrderResponse.class)
                            : toOrderResponse(work.order()),
                    null);
        }
        try {
            Order order = work.order();
            String key = work.key();
            if (OrderIdempotency.STATUS_CANCELLING.equals(work.status())
                    || OrderIdempotency.STATUS_COMPENSATING.equals(work.status())) {
                compensate(work);
                return complete(
                        work,
                        OrderIdempotency.STATUS_CANCELLING.equals(work.status())
                                ? OrderStatus.ROLLBACK
                                : OrderStatus.FAILED,
                        order.getSource(),
                        null);
            }
            String source = "INVENTORY";
            try {
                var inventory =
                        businessStep(
                                work,
                                () ->
                                        inventoryFacade.reserve(
                                                new InventoryReservationRequest(
                                                        key,
                                                        order.getId(),
                                                        order.getItems().stream()
                                                                .map(
                                                                        item ->
                                                                                new ReservationItem(
                                                                                        item
                                                                                                .getProductCode(),
                                                                                        item
                                                                                                .getQuantity()))
                                                                .toList())));
                if (inventory.status() != InventoryReservationStatus.RESERVED
                        && inventory.status() != InventoryReservationStatus.CONSUMED) {
                    throw new IllegalArgumentException(
                            "Inventory reservation failed: " + inventory.message());
                }
                source = "PAYMENT";
                var payment =
                        businessStep(
                                work,
                                () ->
                                        paymentsFacade.reserve(
                                                new PaymentReservationRequest(
                                                        key,
                                                        order.getId(),
                                                        order.getCustomerId(),
                                                        order.getTotalPrice(),
                                                        "USD")));
                if (payment.status() != PaymentStatus.RESERVED
                        && payment.status() != PaymentStatus.CONFIRMED) {
                    throw new IllegalArgumentException(
                            "Payment reservation failed: " + payment.message());
                }
            } catch (RuntimeException ex) {
                // Commit the failure and compensation intent before returning the error.
                return complete(work, OrderStatus.FAILED, source, ex);
            }
            businessStep(work, () -> paymentsFacade.confirm(key));
            businessStep(work, () -> inventoryFacade.consume(key));
            return complete(work, OrderStatus.CONFIRMED, order.getSource(), null);
        } catch (RuntimeException ex) {
            try {
                transactions.execute(
                        tx ->
                                idempotencyRepository.releaseLease(
                                        work.id(), work.status(), work.owner()));
            } catch (RuntimeException releaseFailure) {
                ex.addSuppressed(releaseFailure);
            }
            throw ex;
        }
    }

    private SagaResult complete(
            ClaimWork work, OrderStatus status, String source, RuntimeException failure) {
        return transactions.execute(
                tx -> {
                    Order order =
                            orderRepository
                                    .findOrderById(work.order().getId())
                                    .orElseThrow(
                                            () -> new OrderNotFoundException(work.order().getId()));
                    order.setStatus(status)
                            .setSource(source)
                            .setLastModifiedDate(LocalDateTime.now());
                    OrderResponse response = toOrderResponse(order);
                    String claimStatus =
                            failure != null
                                    ? OrderIdempotency.STATUS_COMPENSATING
                                    : status == OrderStatus.FAILED
                                            ? OrderIdempotency.STATUS_FAILED
                                            : OrderIdempotency.STATUS_COMPLETED;
                    String payload =
                            status == OrderStatus.CONFIRMED
                                            || (status == OrderStatus.ROLLBACK
                                                    && work.responsePayload() == null)
                                    ? objectMapper.writeValueAsString(response)
                                    : work.responsePayload();
                    if (idempotencyRepository.completeClaim(
                                    work.id(),
                                    work.status(),
                                    work.owner(),
                                    LocalDateTime.now(),
                                    claimStatus,
                                    payload)
                            != 1) {
                        throw new IdempotencyConflictException(
                                "Order processing lease is no longer owned");
                    }
                    orderRepository.save(order);
                    if (status == OrderStatus.CONFIRMED) {
                        eventPublisher.publishEvent(
                                new OrderConfirmedEvent(
                                        order.getId(),
                                        order.getCustomerId(),
                                        order.getTotalPrice(),
                                        "USD",
                                        LocalDateTime.now()));
                    }
                    if (status == OrderStatus.CONFIRMED || status == OrderStatus.ROLLBACK) {
                        eventPublisher.publishEvent(toOrderDto(order));
                    }
                    return new SagaResult(failure == null ? response : null, failure);
                });
    }

    private static String operationKey(Long orderId) {
        return "order-" + orderId;
    }

    private void compensate(ClaimWork work) {
        Order order = work.order();
        String key = work.key();
        var payment = paymentsFacade.getPaymentStatus(key);
        if (order.getStatus() == OrderStatus.CONFIRMED && payment.isEmpty()) {
            throw new IllegalStateException(
                    "Confirmed order has no payment reservation: " + order.getId());
        }
        if (payment.isPresent()) {
            if (payment.get().status() == PaymentStatus.CONFIRMED) {
                businessStep(
                        work,
                        () ->
                                paymentsFacade.refund(
                                        new PaymentRefundRequest(
                                                "cancel-" + order.getId(),
                                                order.getId(),
                                                order.getCustomerId(),
                                                order.getTotalPrice(),
                                                "USD",
                                                "Order compensation")));
            } else if (payment.get().status() == PaymentStatus.RESERVED) {
                businessStep(work, () -> paymentsFacade.release(key));
            }
        }
        var inventory = inventoryFacade.getReservationStatus(key);
        if (order.getStatus() == OrderStatus.CONFIRMED && inventory.isEmpty()) {
            throw new IllegalStateException(
                    "Confirmed order has no inventory reservation: " + order.getId());
        }
        if (inventory.isPresent()
                && (inventory.get().status() == InventoryReservationStatus.RESERVED
                        || inventory.get().status() == InventoryReservationStatus.CONSUMED)) {
            businessStep(work, () -> inventoryFacade.cancelReservation(key));
        }
    }

    @Scheduled(fixedDelayString = "${orders.recovery.delay-ms:30000}")
    public void recoverPendingOrders() {
        for (var claim :
                idempotencyRepository.findByStatusIn(
                        List.of(
                                OrderIdempotency.STATUS_PROCESSING,
                                OrderIdempotency.STATUS_COMPENSATING,
                                OrderIdempotency.STATUS_CANCELLING))) {
            try {
                process(claim.getId());
            } catch (RuntimeException ex) {
                log.warn("Order recovery remains pending for order {}", claim.getOrderId(), ex);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderResponse> findOrderById(Long id) {
        return orderRepository.findOrderById(id).map(this::toOrderResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<OrderResponse> findAllOrders(
            int pageNo, int pageSize, String sortBy, String sortDir) {
        Sort sort =
                "asc".equalsIgnoreCase(sortDir)
                        ? Sort.by(sortBy).ascending()
                        : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);
        Page<Long> page = orderRepository.findAllOrders(pageable);
        return mapIdsToPagedResult(page);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<OrderResponse> findOrdersByCustomerId(
            Long customerId, int pageNo, int pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize, Sort.by("id").ascending());
        Page<Long> page = orderRepository.findAllOrdersByCustomerId(customerId, pageable);
        return mapIdsToPagedResult(page);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<OrderResponse> searchOrders(
            String term,
            String mode,
            Long customerId,
            String statusValue,
            Double threshold,
            int pageNo,
            int pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize, Sort.by("id").ascending());
        OrderStatus status = statusValue == null ? null : OrderStatus.valueOf(statusValue);
        Page<Long> page;
        if ("similarity".equalsIgnoreCase(mode)) {
            double actualThreshold = threshold != null ? threshold : 0.3;
            page =
                    orderRepository.searchOrdersBySimilarity(
                            term,
                            actualThreshold,
                            customerId,
                            status != null ? status.name() : null,
                            pageable);
        } else {
            page = orderRepository.searchOrdersByKeyword(term, customerId, status, pageable);
        }
        return mapIdsToPagedResult(page);
    }

    @Override
    @Transactional
    public OrderResponse updateOrder(Long id, OrderRequest request) {
        Order order =
                orderRepository.findOrderById(id).orElseThrow(() -> new OrderNotFoundException(id));
        if (request.deliveryAddress() != null) {
            order.setDeliveryAddress(request.deliveryAddress());
        }
        order.setLastModifiedDate(LocalDateTime.now());
        Order updated = orderRepository.save(order);
        return toOrderResponse(updated);
    }

    @Override
    @Transactional
    public void deleteOrderById(Long id) {
        orderRepository.deleteById(id);
    }

    @Override
    public void cancelOrder(Long id) {
        Long claimId =
                transactions.execute(
                        tx -> {
                            var claim =
                                    idempotencyRepository
                                            .findByOrderId(id)
                                            .orElseThrow(
                                                    () ->
                                                            new IllegalArgumentException(
                                                                    "Order has no recoverable reservation references: "
                                                                            + id));
                            claim =
                                    idempotencyRepository
                                            .findLockedById(claim.getId())
                                            .orElseThrow();
                            Order order =
                                    orderRepository
                                            .findOrderById(id)
                                            .orElseThrow(() -> new OrderNotFoundException(id));
                            if (order.getStatus() != OrderStatus.ROLLBACK) {
                                requireAvailableClaim(claim);
                                claim.setStatus(OrderIdempotency.STATUS_CANCELLING)
                                        .setClaimOwner(null)
                                        .setLeaseExpiresAt(null);
                                idempotencyRepository.save(claim);
                            }
                            return claim.getId();
                        });
        process(claimId);
    }

    @Override
    public List<OrderResponse> saveBatchOrders(List<OrderRequest> requests) {
        if (requests == null || requests.isEmpty()) return List.of();
        return requests.stream()
                .map(
                        request -> {
                            try {
                                return createOrder(request, UUID.randomUUID().toString());
                            } catch (RuntimeException ex) {
                                log.warn("Batch order creation failed", ex);
                                return OrderResponse.emptyResponse(null);
                            }
                        })
                .toList();
    }

    private PagedResult<OrderResponse> mapIdsToPagedResult(Page<Long> page) {
        if (page.isEmpty()) {
            return new PagedResult<>(
                    List.of(),
                    page.getTotalElements(),
                    page.getNumber(),
                    page.getTotalPages(),
                    page.isFirst(),
                    page.isLast(),
                    page.hasNext(),
                    page.hasPrevious());
        }

        List<Order> orders = orderRepository.findByIdIn(page.getContent());
        Map<Long, Integer> pos = new HashMap<>();
        for (int i = 0; i < page.getContent().size(); i++) {
            pos.put(page.getContent().get(i), i);
        }

        List<OrderResponse> responses =
                orders.stream()
                        .sorted(Comparator.comparingInt(o -> pos.getOrDefault(o.getId(), 0)))
                        .map(this::toOrderResponse)
                        .toList();

        return new PagedResult<>(
                responses,
                page.getTotalElements(),
                page.getNumber(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.hasNext(),
                page.hasPrevious());
    }

    private OrderResponse toOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses =
                order.getItems() == null
                        ? List.of()
                        : order.getItems().stream()
                                .map(
                                        item ->
                                                new OrderItemResponse(
                                                        item.getId(),
                                                        item.getProductCode(),
                                                        item.getQuantity(),
                                                        item.getProductPrice(),
                                                        item.getProductPrice()
                                                                .multiply(
                                                                        BigDecimal.valueOf(
                                                                                item.getQuantity()))
                                                                .setScale(2, RoundingMode.HALF_UP)))
                                .toList();

        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus().name(),
                order.getSource(),
                order.getDeliveryAddress(),
                order.getCreatedDate(),
                order.getTotalPrice(),
                itemResponses);
    }

    private OrderDto toOrderDto(Order order) {
        List<OrderDto.OrderItemDto> items =
                order.getItems() == null
                        ? List.of()
                        : order.getItems().stream()
                                .map(
                                        item ->
                                                new OrderDto.OrderItemDto(
                                                        item.getId(),
                                                        item.getProductCode(),
                                                        item.getQuantity(),
                                                        item.getProductPrice()))
                                .toList();

        return new OrderDto(
                order.getId(),
                order.getCustomerId(),
                order.getStatus().name(),
                order.getSource(),
                items);
    }
}
