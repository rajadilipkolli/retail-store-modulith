/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders.internal.stream;

import com.example.retail.orders.internal.domain.Order;
import com.example.retail.orders.internal.persistence.OrderRepository;
import com.example.retail.shared.orders.OrderDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class OrderStreamStoreService {

    private static final Logger log = LoggerFactory.getLogger(OrderStreamStoreService.class);

    private final Map<Long, OrderDto> streamStore = new ConcurrentHashMap<>();
    private final OrderRepository orderRepository;

    public OrderStreamStoreService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public void put(OrderDto orderDto) {
        if (orderDto != null && orderDto.orderId() != null) {
            log.info("Putting order {} into RabbitMQ stream store", orderDto.orderId());
            streamStore.put(orderDto.orderId(), orderDto);
        }
    }

    public List<OrderDto> getAllOrders(int pageNo, int pageSize) {
        log.info(
                "Fetching orders from stream store with pageNo: {}, pageSize: {}",
                pageNo,
                pageSize);
        if (!streamStore.isEmpty()) {
            List<OrderDto> all = new ArrayList<>(streamStore.values());
            int start = pageNo * pageSize;
            if (start >= all.size()) {
                return List.of();
            }
            int end = Math.min(start + pageSize, all.size());
            return all.subList(start, end);
        }

        // Fallback to database if stream store has not yet been hydrated
        Pageable pageable = PageRequest.of(pageNo, pageSize, Sort.by("id").ascending());
        Page<Order> page = orderRepository.findAll(pageable);
        List<Order> orders =
                orderRepository.findByIdIn(page.getContent().stream().map(Order::getId).toList());
        return orders.stream().map(this::toOrderDto).toList();
    }

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
