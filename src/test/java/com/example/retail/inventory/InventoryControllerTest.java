/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.example.retail.inventory.internal.application.InventoryService;
import com.example.retail.inventory.internal.domain.IdempotencyConflictException;
import com.example.retail.inventory.internal.domain.InvalidReservationTransitionException;
import com.example.retail.inventory.internal.domain.InventoryConflictException;
import com.example.retail.inventory.internal.domain.ProductAlreadyExistsException;
import com.example.retail.inventory.internal.web.InventoryController;
import com.example.retail.inventory.internal.web.InventoryExceptionHandler;
import com.example.retail.shared.inventory.InventoryRequest;
import com.example.retail.shared.inventory.InventoryReservationRequest;
import com.example.retail.shared.inventory.InventoryReservationResult;
import com.example.retail.shared.inventory.InventoryReservationStatus;
import com.example.retail.shared.inventory.InventoryResponse;
import com.example.retail.shared.inventory.ReservationItem;
import com.example.retail.shared.models.PagedResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(controllers = InventoryController.class)
@Import(InventoryExceptionHandler.class)
class InventoryControllerTest {

    @Autowired private MockMvcTester mockMvcTester;

    @MockitoBean private InventoryService inventoryService;

    @Test
    @DisplayName("GET /api/v1/inventory returns paged inventory responses")
    void getAllInventories() {
        InventoryResponse r1 = new InventoryResponse(1L, "P001", 100, 5);
        PagedResult<InventoryResponse> paged = new PagedResult<>(new PageImpl<>(List.of(r1)));
        given(inventoryService.findAllInventories(0, 10, "id", "asc")).willReturn(paged);

        assertThat(mockMvcTester.get().uri("/api/v1/inventory"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data")
                .asArray()
                .hasSize(1);
    }

    @Test
    @DisplayName("GET /api/v1/inventory/{productCode} returns inventory when found or 404")
    void getInventoryByProductCode() {
        InventoryResponse r1 = new InventoryResponse(1L, "P001", 100, 5);
        given(inventoryService.findInventoryByProductCode("P001")).willReturn(Optional.of(r1));
        given(inventoryService.findInventoryByProductCode("UNKNOWN")).willReturn(Optional.empty());

        assertThat(mockMvcTester.get().uri("/api/v1/inventory/P001"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.productCode")
                .isEqualTo("P001");

        assertThat(mockMvcTester.get().uri("/api/v1/inventory/UNKNOWN"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET /api/v1/inventory/{productCode} handles optional delay parameter")
    void getInventoryByProductCode_WithDelay() {
        InventoryResponse r1 = new InventoryResponse(1L, "P001", 100, 5);
        given(inventoryService.findInventoryByProductCode("P001")).willReturn(Optional.of(r1));

        assertThat(mockMvcTester.get().uri("/api/v1/inventory/P001?delay=0"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.productCode")
                .isEqualTo("P001");
    }

    @Test
    void getInventoryByProductCode_RejectsExcessiveDelay() {
        assertThat(mockMvcTester.get().uri("/api/v1/inventory/P001?delay=6"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        org.mockito.Mockito.verifyNoInteractions(inventoryService);
    }

    @Test
    void getInventoryByProductCode_RejectsNegativeDelay() {
        assertThat(mockMvcTester.get().uri("/api/v1/inventory/P001?delay=-1"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        org.mockito.Mockito.verifyNoInteractions(inventoryService);
    }

    @Test
    void getInventoryByProductCode_AcceptsMaximumDelay() {
        given(inventoryService.findInventoryByProductCode("P001"))
                .willReturn(Optional.of(new InventoryResponse(1L, "P001", 10, 0)));
        assertThat(mockMvcTester.get().uri("/api/v1/inventory/P001?delay=5")).hasStatusOk();
    }

    @Test
    void updateInventory_ImmutableCodeReturnsConflict() {
        given(inventoryService.updateInventoryById(eq(1L), any()))
                .willThrow(
                        new InventoryConflictException("Inventory product code cannot be changed"));
        assertThat(
                        mockMvcTester
                                .put()
                                .uri("/api/v1/inventory/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productCode\":\"P002\",\"availableQuantity\":20}"))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void deleteInventory_ActiveReservationReturnsConflict() {
        given(inventoryService.findInventoryById(1L))
                .willReturn(Optional.of(new InventoryResponse(1L, "P001", 10, 1)));
        org.mockito.Mockito.doThrow(
                        new InventoryConflictException(
                                "Inventory with outstanding reservations cannot be deleted"))
                .when(inventoryService)
                .deleteInventoryById(1L);
        assertThat(mockMvcTester.delete().uri("/api/v1/inventory/1"))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("GET /api/v1/inventory/product returns paged inventory for list of codes")
    void getInventoryByProductCodes() {
        InventoryResponse r1 = new InventoryResponse(1L, "P001", 100, 5);
        PagedResult<InventoryResponse> paged = new PagedResult<>(new PageImpl<>(List.of(r1)));
        given(inventoryService.getInventoryByProductCodes(List.of("P001"), 0, 10, "id", "asc"))
                .willReturn(paged);

        assertThat(mockMvcTester.get().uri("/api/v1/inventory/product?codes=P001"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data")
                .asArray()
                .hasSize(1);
    }

    @Test
    @DisplayName("POST /api/v1/inventory/generate updates inventory and returns true")
    void updateInventoryWithRandomValue() {
        assertThat(
                        mockMvcTester
                                .post()
                                .uri("/api/v1/inventory/generate?batchSize=10")
                                .header("Idempotency-Key", "test-gen-key"))
                .hasStatusOk()
                .bodyText()
                .isEqualTo("true");

        verify(inventoryService).updateGeneratedInventory("test-gen-key", 10);
    }

    @Test
    @DisplayName("POST /api/v1/inventory creates inventory and returns 201 Created")
    void createInventory_Success() {
        InventoryResponse response = new InventoryResponse(1L, "P001", 50, 0);
        given(inventoryService.saveInventory(any(InventoryRequest.class))).willReturn(response);

        String json =
                """
                {"productCode": "P001", "availableQuantity": 50}
                """;

        assertThat(
                        mockMvcTester
                                .post()
                                .uri("/api/v1/inventory")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "/api/v1/inventory/P001")
                .bodyJson()
                .extractingPath("$.productCode")
                .isEqualTo("P001");
    }

    @Test
    @DisplayName("POST /api/v1/inventory returns 400 ProblemDetail when validation fails")
    void createInventory_ValidationFailure() {
        String invalidJson =
                """
                {"productCode": "", "availableQuantity": -1}
                """;

        assertThat(
                        mockMvcTester
                                .post()
                                .uri("/api/v1/inventory")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidJson))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Constraint Violation");
    }

    @Test
    @DisplayName("POST /api/v1/inventory returns 409 ProblemDetail on duplicate productCode")
    void createInventory_DuplicateConflict() {
        given(inventoryService.saveInventory(any(InventoryRequest.class)))
                .willThrow(ProductAlreadyExistsException.forProductCode("P001"));

        String json =
                """
                {"productCode": "P001", "availableQuantity": 50}
                """;

        assertThat(
                        mockMvcTester
                                .post()
                                .uri("/api/v1/inventory")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Inventory Conflict");
    }

    @Test
    @DisplayName("PUT /api/v1/inventory/{id} updates inventory or returns 404")
    void updateInventoryById() {
        InventoryResponse response = new InventoryResponse(1L, "P001", 75, 0);
        given(inventoryService.updateInventoryById(eq(1L), any(InventoryRequest.class)))
                .willReturn(Optional.of(response));
        given(inventoryService.updateInventoryById(eq(2L), any(InventoryRequest.class)))
                .willReturn(Optional.empty());

        String json =
                """
                {"productCode": "P001", "availableQuantity": 75}
                """;

        assertThat(
                        mockMvcTester
                                .put()
                                .uri("/api/v1/inventory/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.availableQuantity")
                .isEqualTo(75);

        assertThat(
                        mockMvcTester
                                .put()
                                .uri("/api/v1/inventory/2")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName(
            "PUT /api/v1/inventory/product/{productCode} updates or returns 400 if mismatch or 404")
    void updateInventoryByProductCode() {
        InventoryResponse response = new InventoryResponse(1L, "P001", 80, 0);
        given(
                        inventoryService.updateInventoryByProductCode(
                                eq("P001"), any(InventoryRequest.class)))
                .willReturn(Optional.of(response));
        given(
                        inventoryService.updateInventoryByProductCode(
                                eq("P999"), any(InventoryRequest.class)))
                .willReturn(Optional.empty());

        // Mismatched path variable and body productCode
        String mismatchJson =
                """
                {"productCode": "P002", "availableQuantity": 80}
                """;
        assertThat(
                        mockMvcTester
                                .put()
                                .uri("/api/v1/inventory/product/P001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mismatchJson))
                .hasStatus(HttpStatus.BAD_REQUEST);

        // Matching body and path
        String matchingJson =
                """
                {"productCode": "P001", "availableQuantity": 80}
                """;
        assertThat(
                        mockMvcTester
                                .put()
                                .uri("/api/v1/inventory/product/P001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(matchingJson))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.availableQuantity")
                .isEqualTo(80);

        // Not found
        String notFoundJson =
                """
                {"productCode": "P999", "availableQuantity": 80}
                """;
        assertThat(
                        mockMvcTester
                                .put()
                                .uri("/api/v1/inventory/product/P999")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(notFoundJson))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("DELETE /api/v1/inventory/{id} deletes inventory or returns 404")
    void deleteInventory() {
        InventoryResponse response = new InventoryResponse(1L, "P001", 80, 0);
        given(inventoryService.findInventoryById(1L)).willReturn(Optional.of(response));
        given(inventoryService.findInventoryById(2L)).willReturn(Optional.empty());

        assertThat(mockMvcTester.delete().uri("/api/v1/inventory/1"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.id")
                .isEqualTo(1);
        verify(inventoryService).deleteInventoryById(1L);

        assertThat(mockMvcTester.delete().uri("/api/v1/inventory/2"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("POST /api/v1/inventory/reserve reserves stock or handles errors")
    void reserveStock() {
        InventoryReservationResult result =
                new InventoryReservationResult(
                        "key-res",
                        10L,
                        InventoryReservationStatus.RESERVED,
                        "Stock reserved",
                        List.of(new ReservationItem("P001", 2)));
        given(inventoryService.reserve(any(InventoryReservationRequest.class))).willReturn(result);

        String json =
                """
                {
                    "idempotencyKey": "key-res",
                    "orderId": 10,
                    "items": [{"productCode": "P001", "quantity": 2}]
                }
                """;

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
    }

    @Test
    @DisplayName("POST /api/v1/inventory/reserve handles IdempotencyConflictException")
    void reserveStock_IdempotencyConflict() {
        given(inventoryService.reserve(any(InventoryReservationRequest.class)))
                .willThrow(IdempotencyConflictException.forKey("conflict-key"));

        String json =
                """
                {
                    "idempotencyKey": "conflict-key",
                    "orderId": 10,
                    "items": [{"productCode": "P001", "quantity": 2}]
                }
                """;

        assertThat(
                        mockMvcTester
                                .post()
                                .uri("/api/v1/inventory/reserve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Idempotency Conflict");
    }

    @Test
    @DisplayName("POST /api/v1/inventory/release/{idempotencyKey} releases reservation")
    void releaseReservation() {
        InventoryReservationResult result =
                new InventoryReservationResult(
                        "key-rel",
                        10L,
                        InventoryReservationStatus.RELEASED,
                        "Released",
                        List.of(new ReservationItem("P001", 2)));
        given(inventoryService.release("key-rel")).willReturn(result);

        assertThat(mockMvcTester.post().uri("/api/v1/inventory/release/key-rel"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("RELEASED");
    }

    @Test
    @DisplayName("POST /api/v1/inventory/confirm/{idempotencyKey} confirms reservation")
    void confirmReservation() {
        InventoryReservationResult result =
                new InventoryReservationResult(
                        "key-con",
                        10L,
                        InventoryReservationStatus.CONSUMED,
                        "Consumed",
                        List.of(new ReservationItem("P001", 2)));
        given(inventoryService.consume("key-con")).willReturn(result);

        assertThat(mockMvcTester.post().uri("/api/v1/inventory/confirm/key-con"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("CONSUMED");
    }

    @Test
    @DisplayName("POST /api/v1/inventory/release/{idempotencyKey} handles invalid transition")
    void releaseReservation_InvalidTransition() {
        given(inventoryService.release("key-invalid"))
                .willThrow(
                        InvalidReservationTransitionException.forTransition(
                                InventoryReservationStatus.CONSUMED, "RELEASE"));

        assertThat(mockMvcTester.post().uri("/api/v1/inventory/release/key-invalid"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Invalid Reservation Transition");
    }

    @Test
    @DisplayName(
            "GET /api/v1/inventory/reservation/{idempotencyKey} returns status or 404 when not found")
    void getReservationStatus() {
        InventoryReservationResult result =
                new InventoryReservationResult(
                        "key-status",
                        10L,
                        InventoryReservationStatus.RESERVED,
                        "Retrieved",
                        List.of(new ReservationItem("P001", 2)));
        given(inventoryService.getReservationStatus("key-status")).willReturn(Optional.of(result));
        given(inventoryService.getReservationStatus("unknown")).willReturn(Optional.empty());

        assertThat(mockMvcTester.get().uri("/api/v1/inventory/reservation/key-status"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("RESERVED");

        assertThat(mockMvcTester.get().uri("/api/v1/inventory/reservation/unknown"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }
}
