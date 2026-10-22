/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.catalog.internal.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String CATALOG_EXCHANGE = "catalog.exchange";
    public static final String CATALOG_EVENTS_QUEUE = "catalog.events";
    public static final String PRODUCT_ROUTING_KEY_PATTERN = "product.*";

    @Bean
    public TopicExchange catalogExchange() {
        return new TopicExchange(CATALOG_EXCHANGE);
    }

    @Bean
    public Queue catalogEventsQueue() {
        return QueueBuilder.durable(CATALOG_EVENTS_QUEUE).build();
    }

    /** Routes messages matching {@code product.*} from the catalog exchange to its events queue. */
    @Bean
    public Binding catalogEventsBinding(Queue catalogEventsQueue, TopicExchange catalogExchange) {
        return BindingBuilder.bind(catalogEventsQueue)
                .to(catalogExchange)
                .with(PRODUCT_ROUTING_KEY_PATTERN);
    }

    /** Creates a JSON converter that trusts types in all shared packages. */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter("com.example.retail.shared.*");
    }
}
