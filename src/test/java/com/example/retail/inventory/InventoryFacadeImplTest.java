/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.example.retail.inventory.internal.application.InventoryFacadeImpl;
import com.example.retail.inventory.internal.application.InventoryService;
import com.example.retail.shared.inventory.InventoryRequest;
import com.example.retail.shared.inventory.InventoryReservationRequest;
import com.example.retail.shared.inventory.InventoryReservationResult;
import com.example.retail.shared.inventory.InventoryReservationStatus;
import com.example.retail.shared.inventory.InventoryResponse;
import com.example.retail.shared.models.PagedResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
class InventoryFacadeImplTest {

    @Mock private InventoryService inventoryService;

    @InjectMocks private InventoryFacadeImpl inventoryFacade;

    @Test
    @DisplayName("InventoryFacade delegates all operations to InventoryService")
    void delegatesOperations() {
        InventoryResponse res = new InventoryResponse(1L, "P001", 10, 0);
        PagedResult<InventoryResponse> paged = new PagedResult<>(new PageImpl<>(List.of(res)));
        given(inventoryService.findAllInventories(0, 10, "id", "asc")).willReturn(paged);
        assertThat(inventoryFacade.findAllInventories(0, 10, "id", "asc")).isEqualTo(paged);

        given(inventoryService.findInventoryById(1L)).willReturn(Optional.of(res));
        assertThat(inventoryFacade.findInventoryById(1L)).contains(res);

        given(inventoryService.findInventoryByProductCode("P001")).willReturn(Optional.of(res));
        assertThat(inventoryFacade.findInventoryByProductCode("P001")).contains(res);

        given(inventoryService.getInventoryByProductCodes(List.of("P001"), 0, 10, "id", "asc"))
                .willReturn(paged);
        assertThat(inventoryFacade.getInventoryByProductCodes(List.of("P001"), 0, 10, "id", "asc"))
                .isEqualTo(paged);

        InventoryRequest req = new InventoryRequest("P001", 10);
        given(inventoryService.saveInventory(req)).willReturn(res);
        assertThat(inventoryFacade.saveInventory(req)).isEqualTo(res);

        given(inventoryService.updateInventoryById(1L, req)).willReturn(Optional.of(res));
        assertThat(inventoryFacade.updateInventoryById(1L, req)).contains(res);

        given(inventoryService.updateInventoryByProductCode("P001", req))
                .willReturn(Optional.of(res));
        assertThat(inventoryFacade.updateInventoryByProductCode("P001", req)).contains(res);

        inventoryFacade.deleteInventoryById(1L);
        verify(inventoryService).deleteInventoryById(1L);

        inventoryFacade.updateGeneratedInventory("gen-1", 50);
        verify(inventoryService).updateGeneratedInventory("gen-1", 50);

        inventoryFacade.initializeProductInventory("P001");
        verify(inventoryService).initializeProductInventory("P001");

        InventoryReservationRequest resReq =
                new InventoryReservationRequest("res-1", 10L, List.of());
        InventoryReservationResult resResult =
                new InventoryReservationResult(
                        "res-1", 10L, InventoryReservationStatus.RESERVED, "ok", List.of());
        given(inventoryService.reserve(resReq)).willReturn(resResult);
        assertThat(inventoryFacade.reserve(resReq)).isEqualTo(resResult);

        given(inventoryService.release("res-1")).willReturn(resResult);
        assertThat(inventoryFacade.release("res-1")).isEqualTo(resResult);

        given(inventoryService.consume("res-1")).willReturn(resResult);
        assertThat(inventoryFacade.consume("res-1")).isEqualTo(resResult);

        given(inventoryService.getReservationStatus("res-1")).willReturn(Optional.of(resResult));
        assertThat(inventoryFacade.getReservationStatus("res-1")).contains(resResult);
    }
}
