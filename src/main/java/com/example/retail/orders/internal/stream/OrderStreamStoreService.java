/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders.internal.stream;

import com.example.retail.orders.internal.domain.Order;
import com.example.retail.orders.internal.persistence.OrderRepository;
import com.example.retail.shared.orders.OrderDto;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderStreamStoreService {

    private static final Logger log = LoggerFactory.getLogger(OrderStreamStoreService.class);

    private final Map<Long, OrderDto> streamStore;
    private final OrderRepository orderRepository;

    /**
     * Creates a bounded cache that evicts the least recently accessed entry when full.
     *
     * @throws IllegalArgumentException if the maximum size is not positive
     */
    public OrderStreamStoreService(
            OrderRepository orderRepository,
            @Value("${orders.stream.cache-max-size:10000}") int maximumSize) {
        if (maximumSize < 1)
            throw new IllegalArgumentException("Cache maximum size must be positive");
        this.orderRepository = orderRepository;
        this.streamStore =
                Collections.synchronizedMap(
                        new LinkedHashMap<>(16, 0.75f, true) {
                            @Override
                            protected boolean removeEldestEntry(Map.Entry<Long, OrderDto> eldest) {
                                return size() > maximumSize;
                            }
                        });
    }

    /** Caches or replaces an order by ID; null orders and orders without IDs are ignored. */
    public void put(OrderDto orderDto) {
        if (orderDto != null && orderDto.orderId() != null) {
            log.info("Putting order {} into RabbitMQ stream store", orderDto.orderId());
            streamStore.put(orderDto.orderId(), orderDto);
        }
    }

    /**
     * Returns a database page sorted by ascending ID, independently of the stream cache. Orders
     * removed between the ID query and detail query are omitted.
     *
     * @param pageNo zero-based page index
     * @param pageSize maximum orders per page; must be positive
     * @throws IllegalArgumentException if the page index is negative or page size is not positive
     */
    @Transactional(readOnly = true)
    public List<OrderDto> getAllOrders(int pageNo, int pageSize) {
        log.info(
                "Fetching orders from stream store with pageNo: {}, pageSize: {}",
                pageNo,
                pageSize);
        Pageable pageable = PageRequest.of(pageNo, pageSize, Sort.by("id").ascending());
        Page<Long> page = orderRepository.findAllOrders(pageable);
        List<Order> orders = orderRepository.findByIdIn(page.getContent());
        Map<Long, Order> byId =
                orders.stream().collect(Collectors.toMap(Order::getId, Function.identity()));
        return page.getContent().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(this::toOrderDto)
                .toList();
    }

    /**
     * Returns the cached order, falling back to the database on a cache miss. Null, nonnumeric,
     * out-of-range, and missing IDs yield an empty result; database failures propagate.
     */
    public Optional<OrderDto> getOrderFromStoreById(String id) {
        try {
            Long orderId = Long.valueOf(id);
            OrderDto cached = streamStore.get(orderId);
            if (cached != null) {
                return Optional.of(cached);
            }
            return orderRepository.findOrderById(orderId).map(this::toOrderDto);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private OrderDto toOrderDto(Order order) {
        List<OrderDto.OrderItemDto> itemDtos =
                order.getItems().stream()
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
                itemDtos);
    }
}
