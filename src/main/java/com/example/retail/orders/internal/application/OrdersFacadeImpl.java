/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders.internal.application;

import com.example.retail.catalog.CatalogFacade;
import com.example.retail.inventory.InventoryFacade;
import com.example.retail.orders.OrdersFacade;
import com.example.retail.orders.internal.config.RabbitMqOrdersConfig;
import com.example.retail.orders.internal.domain.IdempotencyConflictException;
import com.example.retail.orders.internal.domain.Order;
import com.example.retail.orders.internal.domain.OrderIdempotency;
import com.example.retail.orders.internal.domain.OrderItem;
import com.example.retail.orders.internal.domain.OrderNotFoundException;
import com.example.retail.orders.internal.domain.OrderStatus;
import com.example.retail.orders.internal.persistence.OrderIdempotencyRepository;
import com.example.retail.orders.internal.persistence.OrderRepository;
import com.example.retail.orders.internal.stream.OrderStreamStoreService;
import com.example.retail.payments.PaymentsFacade;
import com.example.retail.shared.inventory.InventoryReservationRequest;
import com.example.retail.shared.inventory.InventoryReservationResult;
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
import com.example.retail.shared.payments.PaymentResult;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final OrderStreamStoreService streamStoreService;

    public OrdersFacadeImpl(
            OrderRepository orderRepository,
            OrderIdempotencyRepository idempotencyRepository,
            CatalogFacade catalogFacade,
            InventoryFacade inventoryFacade,
            PaymentsFacade paymentsFacade,
            OrderRequestFingerprinter fingerprinter,
            ApplicationEventPublisher eventPublisher,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            OrderStreamStoreService streamStoreService) {
        this.orderRepository = orderRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.catalogFacade = catalogFacade;
        this.inventoryFacade = inventoryFacade;
        this.paymentsFacade = paymentsFacade;
        this.fingerprinter = fingerprinter;
        this.eventPublisher = eventPublisher;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.streamStoreService = streamStoreService;
    }

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request, String idempotencyKey) {
        if (request == null) {
            throw new IllegalArgumentException("OrderRequest must not be null");
        }
        if (request.customerId() == null || request.customerId() <= 0) {
            throw new IllegalArgumentException("CustomerId must be positive");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("Order items cannot be empty");
        }

        String effectiveKey =
                (idempotencyKey != null && !idempotencyKey.isBlank())
                        ? idempotencyKey.trim()
                        : UUID.randomUUID().toString();

        String fingerprint = fingerprinter.computeFingerprint(request);

        // 1. Check idempotency claim
        Optional<OrderIdempotency> existingOpt =
                idempotencyRepository.findByCustomerIdAndIdempotencyKey(
                        request.customerId(), effectiveKey);

        OrderIdempotency idempotencyRecord;
        if (existingOpt.isPresent()) {
            OrderIdempotency existing = existingOpt.get();
            if (OrderIdempotency.STATUS_COMPLETED.equals(existing.getStatus())) {
                if (!fingerprint.equals(existing.getRequestFingerprint())) {
                    throw new IdempotencyConflictException(
                            "Idempotency key '"
                                    + effectiveKey
                                    + "' was already used for a different order request");
                }
                if (existing.getResponsePayload() != null) {
                    try {
                        return objectMapper.readValue(
                                existing.getResponsePayload(), OrderResponse.class);
                    } catch (Exception e) {
                        log.warn("Failed to deserialize cached response payload", e);
                    }
                }
                return findOrderById(existing.getOrderId())
                        .orElseThrow(() -> new OrderNotFoundException(existing.getOrderId()));
            } else if (OrderIdempotency.STATUS_PROCESSING.equals(existing.getStatus())) {
                throw new IdempotencyConflictException(
                        "Order creation for key '" + effectiveKey + "' is already in progress");
            } else {
                // Previously failed attempt: reset to PROCESSING
                existing.setStatus(OrderIdempotency.STATUS_PROCESSING);
                existing.setRequestFingerprint(fingerprint);
                existing.setLastModifiedDate(LocalDateTime.now());
                try {
                    idempotencyRecord = idempotencyRepository.saveAndFlush(existing);
                } catch (DataIntegrityViolationException e) {
                    throw new IdempotencyConflictException(
                            "Order creation for key '" + effectiveKey + "' is already in progress");
                }
            }
        } else {
            OrderIdempotency newRecord =
                    new OrderIdempotency()
                            .setCustomerId(request.customerId())
                            .setIdempotencyKey(effectiveKey)
                            .setRequestFingerprint(fingerprint)
                            .setStatus(OrderIdempotency.STATUS_PROCESSING)
                            .setCreatedDate(LocalDateTime.now());
            try {
                idempotencyRecord = idempotencyRepository.saveAndFlush(newRecord);
            } catch (DataIntegrityViolationException e) {
                throw new IdempotencyConflictException(
                        "Order creation for key '" + effectiveKey + "' is already in progress");
            }
        }

        // 2. Validate customer existence
        if (paymentsFacade.findCustomerById(request.customerId()).isEmpty()) {
            idempotencyRecord.setStatus(OrderIdempotency.STATUS_FAILED);
            idempotencyRepository.save(idempotencyRecord);
            throw new IllegalArgumentException(
                    "Customer with id " + request.customerId() + " does not exist");
        }

        // 3. Validate products and calculate total price
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest item : request.items()) {
            if (!catalogFacade.existsByProductCode(item.productCode())) {
                idempotencyRecord.setStatus(OrderIdempotency.STATUS_FAILED);
                idempotencyRepository.save(idempotencyRecord);
                throw new IllegalArgumentException(
                        "Product with code '" + item.productCode() + "' does not exist in catalog");
            }
            BigDecimal unitPrice =
                    item.productPrice() != null ? item.productPrice() : BigDecimal.ZERO;
            total = total.add(unitPrice.multiply(BigDecimal.valueOf(item.quantity())));
        }
        BigDecimal totalPrice = total.setScale(2, RoundingMode.HALF_UP);

        // 4. Pre-commit order with status NEW
        Order order =
                new Order()
                        .setCustomerId(request.customerId())
                        .setStatus(OrderStatus.NEW)
                        .setSource("WEB")
                        .setDeliveryAddress(request.deliveryAddress())
                        .setTotalPrice(totalPrice)
                        .setCreatedDate(LocalDateTime.now())
                        .setCreatedBy("system");

        for (OrderItemRequest item : request.items()) {
            OrderItem orderItem =
                    new OrderItem()
                            .setProductCode(item.productCode())
                            .setQuantity(item.quantity())
                            .setProductPrice(item.productPrice());
            order.addOrderItem(orderItem);
        }

        Order savedOrder = orderRepository.save(order);
        idempotencyRecord.setOrderId(savedOrder.getId());
        idempotencyRepository.save(idempotencyRecord);

        // 5. Reserve Inventory
        List<ReservationItem> reservationItems =
                request.items().stream()
                        .map(item -> new ReservationItem(item.productCode(), item.quantity()))
                        .toList();
        InventoryReservationRequest invReq =
                new InventoryReservationRequest(effectiveKey, savedOrder.getId(), reservationItems);
        InventoryReservationResult invResult;
        try {
            invResult = inventoryFacade.reserve(invReq);
        } catch (RuntimeException ex) {
            if ("IdempotencyConflictException".equals(ex.getClass().getSimpleName())) {
                throw new IdempotencyConflictException(
                        "Order creation for key '" + effectiveKey + "' is already in progress");
            }
            throw ex;
        }

        if (invResult.status() != InventoryReservationStatus.RESERVED) {
            savedOrder.setStatus(OrderStatus.FAILED);
            savedOrder.setSource("INVENTORY");
            orderRepository.save(savedOrder);
            idempotencyRecord.setStatus(OrderIdempotency.STATUS_FAILED);
            idempotencyRepository.save(idempotencyRecord);
            throw new IllegalArgumentException(
                    "Inventory reservation failed: " + invResult.message());
        }

        // 6. Reserve Payment
        PaymentReservationRequest payReq =
                new PaymentReservationRequest(
                        effectiveKey, savedOrder.getId(), request.customerId(), totalPrice, "USD");
        PaymentResult payResult = paymentsFacade.reserve(payReq);

        if (payResult.status() != PaymentStatus.RESERVED) {
            // COMPENSATE: release reserved inventory
            log.warn(
                    "Payment reservation failed for order {}. Compensating inventory reservation.",
                    savedOrder.getId());
            inventoryFacade.release(effectiveKey);

            savedOrder.setStatus(OrderStatus.FAILED);
            savedOrder.setSource("PAYMENT");
            orderRepository.save(savedOrder);
            idempotencyRecord.setStatus(OrderIdempotency.STATUS_FAILED);
            idempotencyRepository.save(idempotencyRecord);
            throw new IllegalArgumentException(
                    "Payment reservation failed: " + payResult.message());
        }

        // 7. Confirm payment and consume inventory
        paymentsFacade.confirm(effectiveKey);
        inventoryFacade.consume(effectiveKey);

        savedOrder.setStatus(OrderStatus.CONFIRMED);
        savedOrder.setLastModifiedDate(LocalDateTime.now());
        Order confirmedOrder = orderRepository.save(savedOrder);

        // 8. Publish Events
        OrderConfirmedEvent event =
                new OrderConfirmedEvent(
                        confirmedOrder.getId(),
                        confirmedOrder.getCustomerId(),
                        confirmedOrder.getTotalPrice(),
                        "USD",
                        LocalDateTime.now());
        eventPublisher.publishEvent(event);

        OrderDto orderDto = toOrderDto(confirmedOrder);
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqOrdersConfig.ORDERS_EXCHANGE, "order.confirmed", orderDto);
        } catch (Exception ex) {
            log.warn("Direct RabbitMQ publishing failed; outbox will handle async dispatch", ex);
        }
        streamStoreService.put(orderDto);

        // 9. Cache response and complete idempotency record
        OrderResponse response = toOrderResponse(confirmedOrder);
        try {
            idempotencyRecord.setResponsePayload(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            log.warn("Failed to serialize response payload for idempotency record", e);
        }
        idempotencyRecord.setStatus(OrderIdempotency.STATUS_COMPLETED);
        idempotencyRecord.setLastModifiedDate(LocalDateTime.now());
        idempotencyRepository.save(idempotencyRecord);

        return response;
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
            OrderStatus status,
            Double threshold,
            int pageNo,
            int pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize, Sort.by("id").ascending());
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
    @Transactional
    public void cancelOrder(Long id) {
        Order order =
                orderRepository.findOrderById(id).orElseThrow(() -> new OrderNotFoundException(id));
        if (order.getStatus() == OrderStatus.ROLLBACK || order.getStatus() == OrderStatus.FAILED) {
            return;
        }

        if (order.getStatus() == OrderStatus.CONFIRMED) {
            String cancelKey = "cancel-" + id;
            try {
                paymentsFacade.refund(
                        new PaymentRefundRequest(
                                cancelKey,
                                order.getCustomerId(),
                                order.getId(),
                                order.getTotalPrice(),
                                "USD",
                                "Order cancelled by customer"));
            } catch (Exception e) {
                log.warn(
                        "Payment refund compensation failed during cancellation of order {}",
                        id,
                        e);
            }
        }

        order.setStatus(OrderStatus.ROLLBACK);
        order.setLastModifiedDate(LocalDateTime.now());
        orderRepository.save(order);
    }

    @Override
    @Transactional
    public List<OrderResponse> saveBatchOrders(List<OrderRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        return requests.stream()
                .map(req -> createOrder(req, UUID.randomUUID().toString()))
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
