/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders.internal.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqOrdersConfig {

    public static final String ORDERS_EXCHANGE = "orders.exchange";
    public static final String ORDERS_EVENTS_QUEUE = "orders.events";
    public static final String ORDERS_STREAM_QUEUE = "orders.stream";
    public static final String ORDER_ROUTING_KEY_PATTERN = "order.*";

    @Bean
    public TopicExchange ordersExchange() {
        return new TopicExchange(ORDERS_EXCHANGE);
    }

    @Bean
    public Queue ordersEventsQueue() {
        return QueueBuilder.durable(ORDERS_EVENTS_QUEUE).build();
    }

    @Bean
    public Queue ordersStreamQueue() {
        return QueueBuilder.durable(ORDERS_STREAM_QUEUE).stream().build();
    }

    @Bean
    public Binding ordersEventsBinding(Queue ordersEventsQueue, TopicExchange ordersExchange) {
        return BindingBuilder.bind(ordersEventsQueue)
                .to(ordersExchange)
                .with(ORDER_ROUTING_KEY_PATTERN);
    }

    @Bean
    public Binding ordersStreamBinding(Queue ordersStreamQueue, TopicExchange ordersExchange) {
        return BindingBuilder.bind(ordersStreamQueue)
                .to(ordersExchange)
                .with(ORDER_ROUTING_KEY_PATTERN);
    }
}
