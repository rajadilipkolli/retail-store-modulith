/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.example.retail.orders.internal.application.OrderGeneratorService;
import com.example.retail.orders.internal.domain.IdempotencyConflictException;
import com.example.retail.orders.internal.stream.OrderStreamStoreService;
import com.example.retail.orders.internal.web.OrderController;
import com.example.retail.orders.internal.web.OrderExceptionHandler;
import com.example.retail.shared.models.PagedResult;
import com.example.retail.shared.orders.Address;
import com.example.retail.shared.orders.OrderDto;
import com.example.retail.shared.orders.OrderItemResponse;
import com.example.retail.shared.orders.OrderRequest;
import com.example.retail.shared.orders.OrderResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(controllers = OrderController.class)
@Import(OrderExceptionHandler.class)
class OrderControllerTest {

    @Autowired private MockMvcTester mockMvcTester;

    @MockitoBean private OrdersFacade ordersFacade;
    @MockitoBean private OrderGeneratorService orderGeneratorService;
    @MockitoBean private OrderStreamStoreService orderStreamStoreService;

    private OrderResponse sampleResponse(Long id) {
        Address addr = new Address("123 Main St", "Apt 4B", "New York", "NY", "10001", "USA");
        return new OrderResponse(
                id,
                101L,
                "CONFIRMED",
                "WEB",
                addr,
                LocalDateTime.now(),
                BigDecimal.valueOf(1499.97),
                List.of(
                        new OrderItemResponse(
                                1L,
                                "P001",
                                1,
                                BigDecimal.valueOf(999.99),
                                BigDecimal.valueOf(999.99)),
                        new OrderItemResponse(
                                2L,
                                "P005",
                                2,
                                BigDecimal.valueOf(249.99),
                                BigDecimal.valueOf(499.98))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/orders", "/api/orders"})
    @DisplayName("GET /api/v1/orders and /api/orders return paginated orders")
    void getAllOrders(String basePath) {
        OrderResponse resp = sampleResponse(1L);
        PagedResult<OrderResponse> pagedResult =
                new PagedResult<>(List.of(resp), 1L, 0, 1, true, true, false, false);

        given(ordersFacade.findAllOrders(0, 10, "id", "asc")).willReturn(pagedResult);

        var result = mockMvcTester.get().uri(basePath);
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.data[0].orderId").asString().isEqualTo("1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/orders/1", "/api/orders/1"})
    @DisplayName("GET order by ID returns 200 when found")
    void getOrderByIdSuccess(String uri) {
        given(ordersFacade.findOrderById(1L)).willReturn(Optional.of(sampleResponse(1L)));

        var result = mockMvcTester.get().uri(uri);
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.orderId").asString().isEqualTo("1");
    }

    @Test
    @DisplayName("GET order by ID returns 404 when not found")
    void getOrderByIdNotFound() {
        given(ordersFacade.findOrderById(999L)).willReturn(Optional.empty());

        var result = mockMvcTester.get().uri("/api/v1/orders/999");
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("POST /api/v1/orders creates order and returns 201 Created with Location")
    void createOrderSuccess() {
        OrderResponse resp = sampleResponse(100L);
        given(ordersFacade.createOrder(any(OrderRequest.class), eq("idemp-key-1")))
                .willReturn(resp);

        String json =
                """
                {
                    "customerId": 101,
                    "items": [
                        {"productCode": "P001", "quantity": 1, "productPrice": 999.99}
                    ],
                    "deliveryAddress": {
                        "addressLine1": "123 Main St",
                        "city": "New York",
                        "state": "NY",
                        "zipCode": "10001",
                        "country": "USA"
                    }
                }
                """;

        var result =
                mockMvcTester
                        .post()
                        .uri("/api/v1/orders")
                        .header("Idempotency-Key", "idemp-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).hasHeader("Location", "/api/v1/orders/100");
        assertThat(result).bodyJson().extractingPath("$.orderId").asString().isEqualTo("100");
    }

    @Test
    @DisplayName("POST /api/v1/orders returns 409 Conflict when idempotency conflict occurs")
    void createOrderIdempotencyConflict() {
        given(ordersFacade.createOrder(any(OrderRequest.class), any()))
                .willThrow(new IdempotencyConflictException("Conflict"));

        String json =
                """
                {
                    "customerId": 101,
                    "items": [
                        {"productCode": "P001", "quantity": 1, "productPrice": 999.99}
                    ],
                    "deliveryAddress": {
                        "addressLine1": "123 Main St",
                        "city": "New York",
                        "state": "NY",
                        "zipCode": "10001",
                        "country": "USA"
                    }
                }
                """;

        var result =
                mockMvcTester
                        .post()
                        .uri("/api/v1/orders")
                        .header("Idempotency-Key", "conflict-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("PUT /api/v1/orders/{id} updates order")
    void updateOrderSuccess() {
        OrderResponse resp = sampleResponse(1L);
        given(ordersFacade.updateOrder(eq(1L), any(OrderRequest.class))).willReturn(resp);

        String json =
                """
                {
                    "customerId": 101,
                    "items": [
                        {"productCode": "P001", "quantity": 1, "productPrice": 999.99}
                    ],
                    "deliveryAddress": {
                        "addressLine1": "456 New St",
                        "city": "New York",
                        "state": "NY",
                        "zipCode": "10001",
                        "country": "USA"
                    }
                }
                """;

        var result =
                mockMvcTester
                        .put()
                        .uri("/api/v1/orders/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json);

        assertThat(result).hasStatusOk();
    }

    @Test
    @DisplayName("DELETE /api/v1/orders/{id} deletes order with 202 Accepted")
    void deleteOrderSuccess() {
        var result = mockMvcTester.delete().uri("/api/v1/orders/1");
        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
    }

    @Test
    @DisplayName("GET /api/v1/orders/customer/{id} returns customer orders")
    void getOrdersByCustomer() {
        PagedResult<OrderResponse> pagedResult =
                new PagedResult<>(List.of(sampleResponse(1L)), 1L, 0, 1, true, true, false, false);
        given(ordersFacade.findOrdersByCustomerId(101L, 0, 10)).willReturn(pagedResult);

        var result = mockMvcTester.get().uri("/api/v1/orders/customer/101");
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.data[0].customerId")
                .asString()
                .isEqualTo("101");
    }

    @Test
    @DisplayName("GET /api/v1/orders/search returns matching orders")
    void searchOrdersSuccess() {
        PagedResult<OrderResponse> pagedResult =
                new PagedResult<>(List.of(sampleResponse(1L)), 1L, 0, 1, true, true, false, false);
        given(ordersFacade.searchOrders("P001", "keyword", null, null, null, 0, 10))
                .willReturn(pagedResult);

        var result = mockMvcTester.get().uri("/api/v1/orders/search?term=P001");
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.totalElements").asNumber().isEqualTo(1);
    }

    @Test
    @DisplayName("GET /api/v1/orders/store returns orders from RabbitMQ Stream store")
    void getFromStreamStore() {
        OrderDto dto =
                new OrderDto(
                        1L,
                        101L,
                        "CONFIRMED",
                        "WEB",
                        List.of(
                                new OrderDto.OrderItemDto(
                                        1L, "P001", 1, BigDecimal.valueOf(999.99))));
        given(orderStreamStoreService.getAllOrders(0, 10)).willReturn(List.of(dto));

        var result = mockMvcTester.get().uri("/api/v1/orders/store");
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[0].orderId").asString().isEqualTo("1");
    }

    @Test
    @DisplayName("POST /api/v1/orders/generate triggers batch mock order generation")
    void generateMockOrders() {
        var result =
                mockMvcTester
                        .post()
                        .uri("/api/v1/orders/generate?batchSize=10")
                        .header("Idempotency-Key", "gen-key-1");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.success").asBoolean().isTrue();
    }

    @Test
    void missingDeliveryAddressIsRejectedBeforeProcessing() {
        var result =
                mockMvcTester
                        .post()
                        .uri("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                        {"customerId":101,"items":[{"productCode":"P001","quantity":1,"productPrice":1}]}
                        """);
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        org.mockito.Mockito.verify(ordersFacade, org.mockito.Mockito.never())
                .createOrder(any(), any());
    }
}
