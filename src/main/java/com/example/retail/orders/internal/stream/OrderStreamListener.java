/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders.internal.stream;

import com.example.retail.orders.internal.config.RabbitMqOrdersConfig;
import com.example.retail.shared.orders.OrderDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderStreamListener {

    private static final Logger log = LoggerFactory.getLogger(OrderStreamListener.class);

    private final OrderStreamStoreService orderStreamStoreService;

    public OrderStreamListener(OrderStreamStoreService orderStreamStoreService) {
        this.orderStreamStoreService = orderStreamStoreService;
    }

    @RabbitListener(queues = RabbitMqOrdersConfig.ORDERS_STREAM_QUEUE)
    public void handleOrderStreamMessage(OrderDto orderDto) {
        log.info(
                "Received order from RabbitMQ stream: id={}, status={}",
                orderDto.orderId(),
                orderDto.status());
        orderStreamStoreService.put(orderDto);
    }
}
