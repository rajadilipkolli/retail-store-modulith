/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.retail.catalog.ContainersConfig;
import com.example.retail.orders.internal.config.RabbitMqOrdersConfig;
import com.example.retail.orders.internal.stream.OrderStreamStoreService;
import com.example.retail.shared.orders.OrderDto;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest(properties = "spring.liquibase.contexts=sample-data")
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
class OrderRabbitStreamIT {

    @Autowired
    private org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry registry;

    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private MessageConverter messageConverter;
    @Autowired private OrderStreamStoreService streamStoreService;
    @Autowired private MockMvcTester mockMvcTester;

    @BeforeEach
    void setUp() {
        rabbitTemplate.setMessageConverter(messageConverter);
    }

    @Test
    @DisplayName(
            "RabbitMQ Stream receives order and hydrates stream store queryable via /api/v1/orders/store")
    void rabbitStreamHydratesStore() {
        long uniqueOrderId = System.currentTimeMillis();
        OrderDto orderDto =
                new OrderDto(
                        uniqueOrderId,
                        101L,
                        "CONFIRMED",
                        "WEB",
                        List.of(
                                new OrderDto.OrderItemDto(
                                        1L, "P001", 1, BigDecimal.valueOf(999.99))),
                        "attempt-1");

        var listener = registry.getListenerContainer("orderStreamListener");
        listener.stop();
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqOrdersConfig.ORDERS_EXCHANGE, "order.created", orderDto);

        } finally {
            listener.start();
        }
        // Messages sent while stopped must replay after restart.
        // Await consumption by OrderStreamListener
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () -> {
                            var cached =
                                    streamStoreService.getOrderFromStoreById(
                                            String.valueOf(uniqueOrderId));
                            assertThat(cached).isPresent();
                            assertThat(cached.get().orderId()).isEqualTo(uniqueOrderId);
                        });

        // Query via GET /api/v1/orders/store
        var result = mockMvcTester.get().uri("/api/v1/orders/store/" + uniqueOrderId);
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.orderId")
                .asString()
                .isEqualTo(String.valueOf(uniqueOrderId));
    }
}
