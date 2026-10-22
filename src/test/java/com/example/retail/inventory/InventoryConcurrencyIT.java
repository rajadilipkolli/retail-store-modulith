/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.retail.catalog.ContainersConfig;
import com.example.retail.inventory.internal.domain.Inventory;
import com.example.retail.inventory.internal.persistence.InventoryRepository;
import com.example.retail.shared.inventory.InventoryReservationRequest;
import com.example.retail.shared.inventory.InventoryReservationResult;
import com.example.retail.shared.inventory.InventoryReservationStatus;
import com.example.retail.shared.inventory.ReservationItem;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(ContainersConfig.class)
class InventoryConcurrencyIT {

    @Autowired private InventoryFacade inventoryFacade;

    @Autowired private InventoryRepository inventoryRepository;

    @Test
    @DisplayName("Concurrent reservations never oversell inventory")
    void concurrentReservations_NeverOversell() throws Exception {
        String productCode = "CONC_P001";
        // Create initial stock of 10
        Inventory inventory = new Inventory(null, productCode, 10, 0);
        inventoryRepository.save(inventory);

        int numberOfThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Callable<InventoryReservationResult>> tasks = new ArrayList<>();
        for (int i = 0; i < numberOfThreads; i++) {
            final int index = i;
            tasks.add(
                    () -> {
                        startLatch.await();
                        InventoryReservationRequest request =
                                new InventoryReservationRequest(
                                        "concurrent-key-" + index,
                                        (long) (1000 + index),
                                        List.of(new ReservationItem(productCode, 1)));
                        return inventoryFacade.reserve(request);
                    });
        }

        startLatch.countDown();
        List<Future<InventoryReservationResult>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        int successCount = 0;
        int failureCount = 0;

        for (Future<InventoryReservationResult> future : futures) {
            InventoryReservationResult result = future.get();
            if (result.status() == InventoryReservationStatus.RESERVED) {
                successCount++;
            } else if (result.status() == InventoryReservationStatus.FAILED) {
                failureCount++;
            }
        }

        assertThat(successCount).isEqualTo(10);
        assertThat(failureCount).isEqualTo(10);

        Inventory finalState = inventoryRepository.findByProductCode(productCode).orElseThrow();
        assertThat(finalState.getAvailableQuantity()).isEqualTo(0);
        assertThat(finalState.getReservedItems()).isEqualTo(10);
    }
}
