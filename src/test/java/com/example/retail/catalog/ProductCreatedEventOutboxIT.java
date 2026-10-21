/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.retail.catalog.internal.config.RabbitMqConfig;
import com.example.retail.shared.catalog.ProductCreatedEvent;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import({ContainersConfig.class, ProductCreatedEventOutboxIT.TestListenerConfig.class})
class ProductCreatedEventOutboxIT {

    @Autowired private MockMvcTester mockMvcTester;

    @Autowired private RabbitTemplate rabbitTemplate;

    @Autowired private JdbcClient jdbcClient;

    @Autowired private TestListenerConfig testListenerConfig;

    @TestConfiguration
    static class TestListenerConfig {

        private final BlockingQueue<ProductCreatedEvent> events = new LinkedBlockingQueue<>();

        @RabbitListener(queues = RabbitMqConfig.CATALOG_EVENTS_QUEUE)
        void onProductCreated(ProductCreatedEvent event) {
            events.offer(event);
        }

        BlockingQueue<ProductCreatedEvent> getEvents() {
            return events;
        }
    }

    @BeforeEach
    void setUp() {
        rabbitTemplate.setMessageConverter(new JacksonJsonMessageConverter("*"));
        testListenerConfig.getEvents().clear();
    }

    @Test
    @DisplayName(
            "Creating product stores outbox record and RabbitListener receives ProductCreatedEvent with correct message content")
    void createProduct_PublishesOutboxEventToRabbitMQ_AndListenerReceivesCorrectMessage()
            throws InterruptedException {
        String uniqueCode = "PROD_OUTBOX_" + System.currentTimeMillis();
        String json =
                """
                {
                    "productCode": "%s",
                    "productName": "Test Outbox Smartphone",
                    "description": "Product created to verify outbox event dispatching",
                    "imageUrl": "https://example.com/images/outbox.jpg",
                    "price": 799.99
                }
                """
                        .formatted(uniqueCode);

        assertThat(
                        mockMvcTester
                                .post()
                                .uri("/api/v1/catalog")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.productCode")
                .isEqualTo(uniqueCode);

        // Verify that the outbox record is stored in public schema
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () -> {
                            Long count =
                                    jdbcClient
                                            .sql(
                                                    "SELECT count(*) FROM public.outbox_record WHERE payload LIKE :keyPattern")
                                            .param("keyPattern", "%" + uniqueCode + "%")
                                            .query(Long.class)
                                            .single();
                            assertThat(count).isGreaterThan(0L);
                        });

        // Verify that RabbitListener receives the message and all fields are correct
        ProductCreatedEvent receivedEvent =
                testListenerConfig.getEvents().poll(15, TimeUnit.SECONDS);
        assertThat(receivedEvent)
                .as("RabbitListener should receive the externalized ProductCreatedEvent")
                .isNotNull();
        assertThat(receivedEvent.productCode()).isEqualTo(uniqueCode);
        assertThat(receivedEvent.productName()).isEqualTo("Test Outbox Smartphone");
        assertThat(receivedEvent.description())
                .isEqualTo("Product created to verify outbox event dispatching");
        assertThat(receivedEvent.imageUrl()).isEqualTo("https://example.com/images/outbox.jpg");
        assertThat(receivedEvent.price()).isEqualByComparingTo(new BigDecimal("799.99"));
    }
}
