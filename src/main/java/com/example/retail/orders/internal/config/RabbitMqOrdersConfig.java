/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders.internal.config;

import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@Configuration
public class RabbitMqOrdersConfig {

    public static final String ORDERS_EXCHANGE = "orders.exchange";
    public static final String ORDERS_EVENTS_QUEUE = "orders.events";
    public static final String ORDERS_STREAM_QUEUE = "orders.stream";

    @Bean
    public TopicExchange ordersExchange() {
        return new TopicExchange(ORDERS_EXCHANGE);
    }

    @Bean
    public Queue ordersEventsQueue() {
        return QueueBuilder.durable(ORDERS_EVENTS_QUEUE).build();
    }

    /** Creates a durable RabbitMQ stream queue with a seven-day maximum age. */
    @Bean
    public Queue ordersStreamQueue() {
        return QueueBuilder.durable(ORDERS_STREAM_QUEUE).stream()
                .withArgument("x-max-age", "7D")
                .build();
    }

    @Bean
    public Binding ordersEventsBinding(Queue ordersEventsQueue, TopicExchange ordersExchange) {
        return BindingBuilder.bind(ordersEventsQueue).to(ordersExchange).with("order.confirmed");
    }

    @Bean
    public Binding ordersStreamBinding(Queue ordersStreamQueue, TopicExchange ordersExchange) {
        return BindingBuilder.bind(ordersStreamQueue).to(ordersExchange).with("order.created");
    }

    /** Configures stream consumers to start at the first retained entry. */
    @Bean
    public SimpleRabbitListenerContainerFactory orderStreamListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory) {
        var factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setContainerCustomizer(
                container -> container.setConsumerArguments(Map.of("x-stream-offset", "first")));
        return factory;
    }
}
