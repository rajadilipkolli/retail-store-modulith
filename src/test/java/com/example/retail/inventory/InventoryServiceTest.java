/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.retail.inventory.internal.application.InventoryService;
import com.example.retail.inventory.internal.domain.IdempotencyConflictException;
import com.example.retail.inventory.internal.domain.InvalidReservationTransitionException;
import com.example.retail.inventory.internal.domain.Inventory;
import com.example.retail.inventory.internal.domain.InventoryNotFoundException;
import com.example.retail.inventory.internal.domain.InventoryReservation;
import com.example.retail.inventory.internal.domain.ProductAlreadyExistsException;
import com.example.retail.inventory.internal.persistence.InventoryRepository;
import com.example.retail.inventory.internal.persistence.InventoryReservationRepository;
import com.example.retail.shared.inventory.InventoryProcessedEvent;
import com.example.retail.shared.inventory.InventoryRequest;
import com.example.retail.shared.inventory.InventoryReservationRequest;
import com.example.retail.shared.inventory.InventoryReservationResult;
import com.example.retail.shared.inventory.InventoryReservationStatus;
import com.example.retail.shared.inventory.InventoryResponse;
import com.example.retail.shared.inventory.ReservationItem;
import com.example.retail.shared.models.PagedResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock private InventoryRepository inventoryRepository;

    @Mock private InventoryReservationRepository reservationRepository;

    @Mock private ApplicationEventPublisher eventPublisher;

    private ObjectMapper objectMapper;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        inventoryService =
                new InventoryService(
                        inventoryRepository, reservationRepository, eventPublisher, objectMapper);
    }

    @Test
    @DisplayName("findAllInventories returns paged results with asc and desc sort")
    void findAllInventories_Sort() {
        Inventory inv = new Inventory(1L, "P001", 10, 2);
        Pageable pageableAsc = PageRequest.of(0, 10, Sort.by("id").ascending());
        Pageable pageableDesc = PageRequest.of(0, 10, Sort.by("id").descending());

        given(inventoryRepository.findAll(pageableAsc))
                .willReturn(new PageImpl<>(List.of(inv), pageableAsc, 1));
        given(inventoryRepository.findAll(pageableDesc))
                .willReturn(new PageImpl<>(List.of(inv), pageableDesc, 1));

        PagedResult<InventoryResponse> ascResult =
                inventoryService.findAllInventories(0, 10, "id", "asc");
        assertThat(ascResult.data()).hasSize(1);
        assertThat(ascResult.data().getFirst().productCode()).isEqualTo("P001");

        PagedResult<InventoryResponse> descResult =
                inventoryService.findAllInventories(0, 10, "id", "desc");
        assertThat(descResult.data()).hasSize(1);
    }

    @Test
    @DisplayName("findInventoryById returns mapped response or empty")
    void findInventoryById() {
        Inventory inv = new Inventory(1L, "P001", 10, 0);
        given(inventoryRepository.findById(1L)).willReturn(Optional.of(inv));
        given(inventoryRepository.findById(2L)).willReturn(Optional.empty());

        assertThat(inventoryService.findInventoryById(1L)).isPresent();
        assertThat(inventoryService.findInventoryById(2L)).isEmpty();
    }

    @Test
    @DisplayName("findInventoryByProductCode returns mapped response or empty")
    void findInventoryByProductCode() {
        Inventory inv = new Inventory(1L, "P001", 10, 0);
        given(inventoryRepository.findByProductCode("P001")).willReturn(Optional.of(inv));
        given(inventoryRepository.findByProductCode("UNKNOWN")).willReturn(Optional.empty());

        assertThat(inventoryService.findInventoryByProductCode("P001")).isPresent();
        assertThat(inventoryService.findInventoryByProductCode("UNKNOWN")).isEmpty();
    }

    @Test
    @DisplayName("getInventoryByProductCodes returns paged results")
    void getInventoryByProductCodes() {
        Inventory inv = new Inventory(1L, "P001", 10, 0);
        Pageable pageable = PageRequest.of(0, 5, Sort.by("productCode").descending());
        given(inventoryRepository.findByProductCodeIn(List.of("P001"), pageable))
                .willReturn(new PageImpl<>(List.of(inv), pageable, 1));

        PagedResult<InventoryResponse> result =
                inventoryService.getInventoryByProductCodes(
                        List.of("P001"), 0, 5, "productCode", "desc");
        assertThat(result.data()).hasSize(1);
    }

    @Test
    @DisplayName("saveInventory creates new inventory when product code is unique")
    void saveInventory_Success() {
        InventoryRequest request = new InventoryRequest("P001", 50);
        given(inventoryRepository.existsByProductCode("P001")).willReturn(false);
        given(inventoryRepository.save(any(Inventory.class)))
                .willAnswer(
                        invocation -> {
                            Inventory input = invocation.getArgument(0);
                            input.setId(10L);
                            return input;
                        });

        InventoryResponse response = inventoryService.saveInventory(request);
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.productCode()).isEqualTo("P001");
        assertThat(response.availableQuantity()).isEqualTo(50);
        assertThat(response.reservedItems()).isZero();
    }

    @Test
    @DisplayName("saveInventory throws ProductAlreadyExistsException when productCode exists")
    void saveInventory_DuplicateThrows() {
        InventoryRequest request = new InventoryRequest("P001", 50);
        given(inventoryRepository.existsByProductCode("P001")).willReturn(true);

        assertThatThrownBy(() -> inventoryService.saveInventory(request))
                .isInstanceOf(ProductAlreadyExistsException.class)
                .hasMessageContaining("P001");
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateInventoryById updates successfully when code is unchanged or unique")
    void updateInventoryById_Success() {
        Inventory inv = new Inventory(1L, "P001", 10, 0);
        given(inventoryRepository.findById(1L)).willReturn(Optional.of(inv));
        given(inventoryRepository.save(any(Inventory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        InventoryRequest sameCode = new InventoryRequest("P001", 30);
        Optional<InventoryResponse> res1 = inventoryService.updateInventoryById(1L, sameCode);
        assertThat(res1).isPresent();
        assertThat(res1.get().availableQuantity()).isEqualTo(30);

        InventoryRequest newCode = new InventoryRequest("P002", 40);
        given(inventoryRepository.existsByProductCode("P002")).willReturn(false);
        Optional<InventoryResponse> res2 = inventoryService.updateInventoryById(1L, newCode);
        assertThat(res2).isPresent();
        assertThat(res2.get().productCode()).isEqualTo("P002");
        assertThat(res2.get().availableQuantity()).isEqualTo(40);
    }

    @Test
    @DisplayName("updateInventoryById throws ProductAlreadyExistsException on collision")
    void updateInventoryById_Collision() {
        Inventory inv = new Inventory(1L, "P001", 10, 0);
        given(inventoryRepository.findById(1L)).willReturn(Optional.of(inv));
        given(inventoryRepository.existsByProductCode("P002")).willReturn(true);

        InventoryRequest req = new InventoryRequest("P002", 20);
        assertThatThrownBy(() -> inventoryService.updateInventoryById(1L, req))
                .isInstanceOf(ProductAlreadyExistsException.class)
                .hasMessageContaining("P002");
    }

    @Test
    @DisplayName("updateInventoryById returns empty when ID not found")
    void updateInventoryById_NotFound() {
        given(inventoryRepository.findById(99L)).willReturn(Optional.empty());
        Optional<InventoryResponse> res =
                inventoryService.updateInventoryById(99L, new InventoryRequest("P001", 10));
        assertThat(res).isEmpty();
    }

    @Test
    @DisplayName("updateInventoryByProductCode updates successfully or returns empty")
    void updateInventoryByProductCode() {
        Inventory inv = new Inventory(1L, "P001", 10, 0);
        given(inventoryRepository.findByProductCode("P001")).willReturn(Optional.of(inv));
        given(inventoryRepository.findByProductCode("P999")).willReturn(Optional.empty());
        given(inventoryRepository.save(any(Inventory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Optional<InventoryResponse> res =
                inventoryService.updateInventoryByProductCode(
                        "P001", new InventoryRequest("P001", 100));
        assertThat(res).isPresent();
        assertThat(res.get().availableQuantity()).isEqualTo(100);

        assertThat(
                        inventoryService.updateInventoryByProductCode(
                                "P999", new InventoryRequest("P999", 5)))
                .isEmpty();
    }

    @Test
    @DisplayName("deleteInventoryById calls repository delete")
    void deleteInventoryById() {
        inventoryService.deleteInventoryById(1L);
        verify(inventoryRepository).deleteById(1L);
    }

    @Test
    @DisplayName("updateGeneratedInventory handles null, duplicates, batchSize, and updates")
    void updateGeneratedInventory() {
        inventoryService.updateGeneratedInventory(null, 5);
        verify(inventoryRepository, never()).findByProductCodeIn(anyList());

        inventoryService.updateGeneratedInventory("gen-key", 2);
        verify(inventoryRepository).findByProductCodeIn(anyList());

        // Duplicate call with same key should be ignored
        inventoryService.updateGeneratedInventory("gen-key", 2);
        verify(inventoryRepository).findByProductCodeIn(anyList());

        // Test with null and large batchSize
        Inventory matching1 = new Inventory(1L, "ProductCode_key2_0", 0, 0);
        given(inventoryRepository.findByProductCodeIn(anyList())).willReturn(List.of(matching1));
        inventoryService.updateGeneratedInventory("key2", null);
        verify(inventoryRepository, org.mockito.Mockito.times(2)).saveAll(anyList());
    }

    @Test
    @DisplayName("initializeProductInventory creates entry if not existing")
    void initializeProductInventory() {
        given(inventoryRepository.existsByProductCode("P001")).willReturn(true);
        inventoryService.initializeProductInventory("P001");
        verify(inventoryRepository, never()).save(any());

        given(inventoryRepository.existsByProductCode("P002")).willReturn(false);
        inventoryService.initializeProductInventory("P002");
        verify(inventoryRepository).save(any(Inventory.class));
    }

    @Test
    @DisplayName("reserve returns existing idempotent result when orderId and items match")
    void reserve_IdempotentSuccess() throws Exception {
        List<ReservationItem> items = List.of(new ReservationItem("P001", 2));
        String json = objectMapper.writeValueAsString(items);
        InventoryReservation existing =
                new InventoryReservation(
                        1L,
                        "key-idem",
                        100L,
                        InventoryReservationStatus.RESERVED,
                        json,
                        Instant.now(),
                        Instant.now());
        given(reservationRepository.findByIdempotencyKey("key-idem"))
                .willReturn(Optional.of(existing));

        InventoryReservationRequest request =
                new InventoryReservationRequest("key-idem", 100L, items);
        InventoryReservationResult result = inventoryService.reserve(request);

        assertThat(result.status()).isEqualTo(InventoryReservationStatus.RESERVED);
        assertThat(result.orderId()).isEqualTo(100L);
        verify(inventoryRepository, never()).findWithLockByProductCodeIn(anyList());
    }

    @Test
    @DisplayName("reserve throws IdempotencyConflictException when orderId or items differ")
    void reserve_IdempotentConflict() throws Exception {
        List<ReservationItem> items = List.of(new ReservationItem("P001", 2));
        String json = objectMapper.writeValueAsString(items);
        InventoryReservation existing =
                new InventoryReservation(
                        1L,
                        "key-idem",
                        100L,
                        InventoryReservationStatus.RESERVED,
                        json,
                        Instant.now(),
                        Instant.now());
        given(reservationRepository.findByIdempotencyKey("key-idem"))
                .willReturn(Optional.of(existing));

        // Different orderId
        InventoryReservationRequest diffOrder =
                new InventoryReservationRequest("key-idem", 999L, items);
        assertThatThrownBy(() -> inventoryService.reserve(diffOrder))
                .isInstanceOf(IdempotencyConflictException.class);

        // Different items
        InventoryReservationRequest diffItems =
                new InventoryReservationRequest(
                        "key-idem", 100L, List.of(new ReservationItem("P002", 5)));
        assertThatThrownBy(() -> inventoryService.reserve(diffItems))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    @DisplayName("reserve records FAILED and publishes event when product does not exist")
    void reserve_MissingProductFails() {
        List<ReservationItem> items =
                List.of(new ReservationItem("P001", 1), new ReservationItem("P002", 2));
        InventoryReservationRequest request =
                new InventoryReservationRequest("key-miss", 100L, items);

        given(reservationRepository.findByIdempotencyKey("key-miss")).willReturn(Optional.empty());
        // Return only P001, missing P002
        Inventory inv1 = new Inventory(1L, "P001", 10, 0);
        given(inventoryRepository.findWithLockByProductCodeIn(List.of("P001", "P002")))
                .willReturn(List.of(inv1));

        InventoryReservationResult result = inventoryService.reserve(request);
        assertThat(result.status()).isEqualTo(InventoryReservationStatus.FAILED);
        assertThat(result.message()).contains("not found");

        verify(reservationRepository).save(any(InventoryReservation.class));
        verify(eventPublisher).publishEvent(any(InventoryProcessedEvent.class));
    }

    @Test
    @DisplayName("reserve records FAILED and publishes event when stock is insufficient")
    void reserve_InsufficientStockFails() {
        List<ReservationItem> items = List.of(new ReservationItem("P001", 10));
        InventoryReservationRequest request =
                new InventoryReservationRequest("key-low", 100L, items);

        given(reservationRepository.findByIdempotencyKey("key-low")).willReturn(Optional.empty());
        Inventory inv1 = new Inventory(1L, "P001", 5, 0); // available is only 5
        given(inventoryRepository.findWithLockByProductCodeIn(List.of("P001")))
                .willReturn(List.of(inv1));

        InventoryReservationResult result = inventoryService.reserve(request);
        assertThat(result.status()).isEqualTo(InventoryReservationStatus.FAILED);
        assertThat(result.message()).contains("Insufficient stock");

        verify(reservationRepository).save(any(InventoryReservation.class));
        verify(eventPublisher).publishEvent(any(InventoryProcessedEvent.class));
    }

    @Test
    @DisplayName("reserve succeeds, reserves stock atomically, and publishes event")
    void reserve_Success() {
        List<ReservationItem> items =
                List.of(new ReservationItem("P001", 4), new ReservationItem("P002", 1));
        InventoryReservationRequest request =
                new InventoryReservationRequest("key-ok", 100L, items);

        given(reservationRepository.findByIdempotencyKey("key-ok")).willReturn(Optional.empty());
        Inventory inv1 = new Inventory(1L, "P001", 10, 2);
        Inventory inv2 = new Inventory(2L, "P002", 5, 0);
        given(inventoryRepository.findWithLockByProductCodeIn(List.of("P001", "P002")))
                .willReturn(List.of(inv1, inv2));

        InventoryReservationResult result = inventoryService.reserve(request);

        assertThat(result.status()).isEqualTo(InventoryReservationStatus.RESERVED);
        assertThat(inv1.getAvailableQuantity()).isEqualTo(6);
        assertThat(inv1.getReservedItems()).isEqualTo(6);
        assertThat(inv2.getAvailableQuantity()).isEqualTo(4);
        assertThat(inv2.getReservedItems()).isEqualTo(1);

        verify(inventoryRepository).saveAll(anyList());
        verify(reservationRepository).save(any(InventoryReservation.class));
        verify(eventPublisher).publishEvent(any(InventoryProcessedEvent.class));
    }

    @Test
    @DisplayName("release throws InventoryNotFoundException when reservation is not found")
    void release_NotFound() {
        given(reservationRepository.findByIdempotencyKey("key-none")).willReturn(Optional.empty());
        assertThatThrownBy(() -> inventoryService.release("key-none"))
                .isInstanceOf(InventoryNotFoundException.class);
    }

    @Test
    @DisplayName("release returns idempotent result when already RELEASED")
    void release_AlreadyReleased() throws Exception {
        String json = objectMapper.writeValueAsString(List.of(new ReservationItem("P001", 2)));
        InventoryReservation res =
                new InventoryReservation(
                        1L,
                        "key-rel",
                        100L,
                        InventoryReservationStatus.RELEASED,
                        json,
                        Instant.now(),
                        Instant.now());
        given(reservationRepository.findByIdempotencyKey("key-rel")).willReturn(Optional.of(res));

        InventoryReservationResult result = inventoryService.release("key-rel");
        assertThat(result.status()).isEqualTo(InventoryReservationStatus.RELEASED);
        verify(inventoryRepository, never()).findWithLockByProductCodeIn(anyList());
    }

    @Test
    @DisplayName("release throws InvalidReservationTransitionException for CONSUMED or FAILED")
    void release_InvalidTransitions() throws Exception {
        String json = objectMapper.writeValueAsString(List.of(new ReservationItem("P001", 2)));
        InventoryReservation consumed =
                new InventoryReservation(
                        1L,
                        "key-c",
                        100L,
                        InventoryReservationStatus.CONSUMED,
                        json,
                        Instant.now(),
                        Instant.now());
        given(reservationRepository.findByIdempotencyKey("key-c"))
                .willReturn(Optional.of(consumed));
        assertThatThrownBy(() -> inventoryService.release("key-c"))
                .isInstanceOf(InvalidReservationTransitionException.class);

        InventoryReservation failed =
                new InventoryReservation(
                        2L,
                        "key-f",
                        100L,
                        InventoryReservationStatus.FAILED,
                        json,
                        Instant.now(),
                        Instant.now());
        given(reservationRepository.findByIdempotencyKey("key-f")).willReturn(Optional.of(failed));
        assertThatThrownBy(() -> inventoryService.release("key-f"))
                .isInstanceOf(InvalidReservationTransitionException.class);
    }

    @Test
    @DisplayName("release restores stock and transitions status to RELEASED")
    void release_Success() throws Exception {
        String json = objectMapper.writeValueAsString(List.of(new ReservationItem("P001", 2)));
        InventoryReservation reserved =
                new InventoryReservation(
                        1L,
                        "key-ok",
                        100L,
                        InventoryReservationStatus.RESERVED,
                        json,
                        Instant.now(),
                        Instant.now());
        given(reservationRepository.findByIdempotencyKey("key-ok"))
                .willReturn(Optional.of(reserved));

        Inventory inv = new Inventory(1L, "P001", 8, 2);
        given(inventoryRepository.findWithLockByProductCodeIn(List.of("P001")))
                .willReturn(List.of(inv));

        InventoryReservationResult result = inventoryService.release("key-ok");
        assertThat(result.status()).isEqualTo(InventoryReservationStatus.RELEASED);
        assertThat(inv.getAvailableQuantity()).isEqualTo(10);
        assertThat(inv.getReservedItems()).isEqualTo(0);
        assertThat(reserved.getStatus()).isEqualTo(InventoryReservationStatus.RELEASED);

        verify(inventoryRepository).saveAll(anyList());
        verify(reservationRepository).save(reserved);
    }

    @Test
    @DisplayName("consume throws InventoryNotFoundException when reservation is not found")
    void consume_NotFound() {
        given(reservationRepository.findByIdempotencyKey("key-none")).willReturn(Optional.empty());
        assertThatThrownBy(() -> inventoryService.consume("key-none"))
                .isInstanceOf(InventoryNotFoundException.class);
    }

    @Test
    @DisplayName("consume returns idempotent result when already CONSUMED")
    void consume_AlreadyConsumed() throws Exception {
        String json = objectMapper.writeValueAsString(List.of(new ReservationItem("P001", 2)));
        InventoryReservation res =
                new InventoryReservation(
                        1L,
                        "key-con",
                        100L,
                        InventoryReservationStatus.CONSUMED,
                        json,
                        Instant.now(),
                        Instant.now());
        given(reservationRepository.findByIdempotencyKey("key-con")).willReturn(Optional.of(res));

        InventoryReservationResult result = inventoryService.consume("key-con");
        assertThat(result.status()).isEqualTo(InventoryReservationStatus.CONSUMED);
        verify(inventoryRepository, never()).findWithLockByProductCodeIn(anyList());
    }

    @Test
    @DisplayName("consume throws InvalidReservationTransitionException for RELEASED or FAILED")
    void consume_InvalidTransitions() throws Exception {
        String json = objectMapper.writeValueAsString(List.of(new ReservationItem("P001", 2)));
        InventoryReservation released =
                new InventoryReservation(
                        1L,
                        "key-r",
                        100L,
                        InventoryReservationStatus.RELEASED,
                        json,
                        Instant.now(),
                        Instant.now());
        given(reservationRepository.findByIdempotencyKey("key-r"))
                .willReturn(Optional.of(released));
        assertThatThrownBy(() -> inventoryService.consume("key-r"))
                .isInstanceOf(InvalidReservationTransitionException.class);

        InventoryReservation failed =
                new InventoryReservation(
                        2L,
                        "key-f",
                        100L,
                        InventoryReservationStatus.FAILED,
                        json,
                        Instant.now(),
                        Instant.now());
        given(reservationRepository.findByIdempotencyKey("key-f")).willReturn(Optional.of(failed));
        assertThatThrownBy(() -> inventoryService.consume("key-f"))
                .isInstanceOf(InvalidReservationTransitionException.class);
    }

    @Test
    @DisplayName("consume decrements reserved stock and transitions status to CONSUMED")
    void consume_Success() throws Exception {
        String json = objectMapper.writeValueAsString(List.of(new ReservationItem("P001", 2)));
        InventoryReservation reserved =
                new InventoryReservation(
                        1L,
                        "key-ok",
                        100L,
                        InventoryReservationStatus.RESERVED,
                        json,
                        Instant.now(),
                        Instant.now());
        given(reservationRepository.findByIdempotencyKey("key-ok"))
                .willReturn(Optional.of(reserved));

        Inventory inv = new Inventory(1L, "P001", 8, 2);
        given(inventoryRepository.findWithLockByProductCodeIn(List.of("P001")))
                .willReturn(List.of(inv));

        InventoryReservationResult result = inventoryService.consume("key-ok");
        assertThat(result.status()).isEqualTo(InventoryReservationStatus.CONSUMED);
        assertThat(inv.getAvailableQuantity()).isEqualTo(8); // Available stays same
        assertThat(inv.getReservedItems()).isEqualTo(0); // Reserved decremented
        assertThat(reserved.getStatus()).isEqualTo(InventoryReservationStatus.CONSUMED);

        verify(inventoryRepository).saveAll(anyList());
        verify(reservationRepository).save(reserved);
    }

    @Test
    @DisplayName("getReservationStatus returns result or empty")
    void getReservationStatus() throws Exception {
        String json = objectMapper.writeValueAsString(List.of(new ReservationItem("P001", 2)));
        InventoryReservation res =
                new InventoryReservation(
                        1L,
                        "key-status",
                        100L,
                        InventoryReservationStatus.RESERVED,
                        json,
                        Instant.now(),
                        Instant.now());
        given(reservationRepository.findByIdempotencyKey("key-status"))
                .willReturn(Optional.of(res));
        given(reservationRepository.findByIdempotencyKey("unknown")).willReturn(Optional.empty());

        assertThat(inventoryService.getReservationStatus("key-status")).isPresent();
        assertThat(inventoryService.getReservationStatus("unknown")).isEmpty();
    }
}
