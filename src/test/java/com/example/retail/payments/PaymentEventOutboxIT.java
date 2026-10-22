/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.retail.catalog.ContainersConfig;
import com.example.retail.payments.internal.config.RabbitMqPaymentConfig;
import com.example.retail.shared.payments.PaymentProcessedEvent;
import com.example.retail.shared.payments.PaymentStatus;
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

@SpringBootTest(properties = "spring.liquibase.contexts=sample-data")
@AutoConfigureMockMvc
@Import({ContainersConfig.class, PaymentEventOutboxIT.TestListenerConfig.class})
class PaymentEventOutboxIT {

    @Autowired private MockMvcTester mockMvcTester;
    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private TestListenerConfig testListenerConfig;

    @TestConfiguration
    static class TestListenerConfig {

        private final BlockingQueue<PaymentProcessedEvent> events = new LinkedBlockingQueue<>();

        @RabbitListener(queues = RabbitMqPaymentConfig.PAYMENT_EVENTS_QUEUE)
        void onPaymentProcessed(PaymentProcessedEvent event) {
            events.offer(event);
        }

        BlockingQueue<PaymentProcessedEvent> getEvents() {
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
            "Reserving payment stores outbox record and RabbitListener receives PaymentProcessedEvent")
    void reservePayment_PublishesOutboxEventToRabbitMQ_AndListenerReceivesCorrectMessage()
            throws InterruptedException {
        String idempotencyKey = "outbox-pay-key-" + System.currentTimeMillis();
        // Sample customer 101 has plenty of balance
        String json =
                """
                {
                    "idempotencyKey": "%s",
                    "orderId": 777,
                    "customerId": 101,
                    "amount": 25.00,
                    "currency": "USD"
                }
                """
                        .formatted(idempotencyKey);

        assertThat(
                        mockMvcTester
                                .post()
                                .uri("/api/v1/payments/reserve")
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

        // Verify RabbitListener receives the externalized PaymentProcessedEvent
        PaymentProcessedEvent receivedEvent =
                testListenerConfig.getEvents().poll(15, TimeUnit.SECONDS);
        assertThat(receivedEvent)
                .as("RabbitListener should receive the externalized PaymentProcessedEvent")
                .isNotNull();
        assertThat(receivedEvent.result().idempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(receivedEvent.result().orderId()).isEqualTo(777L);
        assertThat(receivedEvent.result().customerId()).isEqualTo(101L);
        assertThat(receivedEvent.result().status()).isEqualTo(PaymentStatus.RESERVED);
        assertThat(receivedEvent.result().amount()).isEqualByComparingTo("25.00");
        assertThat(receivedEvent.result().currency()).isEqualTo("USD");
    }
}
