/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.retail.catalog.ContainersConfig;
import com.example.retail.inventory.internal.config.RabbitMqInventoryConfig;
import com.example.retail.shared.inventory.InventoryProcessedEvent;
import com.example.retail.shared.inventory.InventoryReservationStatus;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import({ContainersConfig.class, InventoryEventOutboxIT.TestListenerConfig.class})
class InventoryEventOutboxIT {

    @Autowired private MockMvcTester mockMvcTester;

    @Autowired private RabbitTemplate rabbitTemplate;

    @Autowired private JdbcClient jdbcClient;

    @Autowired private TestListenerConfig testListenerConfig;

    @TestConfiguration
    static class TestListenerConfig {

        private final BlockingQueue<InventoryProcessedEvent> events = new LinkedBlockingQueue<>();

        @RabbitListener(queues = RabbitMqInventoryConfig.INVENTORY_EVENTS_QUEUE)
        void onInventoryProcessed(InventoryProcessedEvent event) {
            events.offer(event);
        }

        BlockingQueue<InventoryProcessedEvent> getEvents() {
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
            "Reserving stock stores outbox record and RabbitListener receives InventoryProcessedEvent")
    void reserveStock_PublishesOutboxEventToRabbitMQ_AndListenerReceivesCorrectMessage()
            throws InterruptedException {
        String idempotencyKey = "outbox-test-key-" + System.currentTimeMillis();
        String json =
                """
                {
                    "idempotencyKey": "%s",
                    "orderId": 555,
                    "items": [{"productCode": "P001", "quantity": 1}]
                }
                """
                        .formatted(idempotencyKey);

        assertThat(
                        mockMvcTester
                                .post()
                                .uri("/api/v1/inventory/reserve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("RESERVED");

        // Verify outbox record in public.outbox_record
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () -> {
                            Long count =
                                    jdbcClient
                                            .sql(
                                                    "SELECT count(*) FROM public.outbox_record WHERE payload LIKE :keyPattern")
                                            .param("keyPattern", "%" + idempotencyKey + "%")
                                            .query(Long.class)
                                            .single();
                            assertThat(count).isGreaterThan(0L);
                        });

        // Verify RabbitListener receives the externalized InventoryProcessedEvent
        InventoryProcessedEvent receivedEvent =
                testListenerConfig.getEvents().poll(15, TimeUnit.SECONDS);
        assertThat(receivedEvent)
                .as("RabbitListener should receive the externalized InventoryProcessedEvent")
                .isNotNull();
        assertThat(receivedEvent.result().idempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(receivedEvent.result().orderId()).isEqualTo(555L);
        assertThat(receivedEvent.result().status()).isEqualTo(InventoryReservationStatus.RESERVED);
        assertThat(receivedEvent.result().items()).hasSize(1);
        assertThat(receivedEvent.result().items().getFirst().productCode()).isEqualTo("P001");
        assertThat(receivedEvent.result().items().getFirst().quantity()).isEqualTo(1);
    }
}
