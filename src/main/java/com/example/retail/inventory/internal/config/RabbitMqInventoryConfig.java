/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.inventory.internal.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqInventoryConfig {

    public static final String INVENTORY_EXCHANGE = "inventory.exchange";
    public static final String INVENTORY_EVENTS_QUEUE = "inventory.events";
    public static final String INVENTORY_ROUTING_KEY_PATTERN = "inventory.*";

    @Bean
    public TopicExchange inventoryExchange() {
        return new TopicExchange(INVENTORY_EXCHANGE);
    }

    @Bean
    public Queue inventoryEventsQueue() {
        return QueueBuilder.durable(INVENTORY_EVENTS_QUEUE).build();
    }

    @Bean
    public Binding inventoryEventsBinding(
            Queue inventoryEventsQueue, TopicExchange inventoryExchange) {
        return BindingBuilder.bind(inventoryEventsQueue)
                .to(inventoryExchange)
                .with(INVENTORY_ROUTING_KEY_PATTERN);
    }
}
