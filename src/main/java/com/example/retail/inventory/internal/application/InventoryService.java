/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.inventory.internal.application;

import com.example.retail.inventory.internal.domain.IdempotencyConflictException;
import com.example.retail.inventory.internal.domain.InvalidReservationTransitionException;
import com.example.retail.inventory.internal.domain.Inventory;
import com.example.retail.inventory.internal.domain.InventoryConflictException;
import com.example.retail.inventory.internal.domain.InventoryNotFoundException;
import com.example.retail.inventory.internal.domain.InventoryReservation;
import com.example.retail.inventory.internal.domain.ProductAlreadyExistsException;
import com.example.retail.inventory.internal.persistence.GeneratedInventoryRepository;
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
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class InventoryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InventoryService.class);
    private static final Random RANDOM = new SecureRandom();

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final GeneratedInventoryRepository generatedInventoryRepository;

    public InventoryService(
            InventoryRepository inventoryRepository,
            InventoryReservationRepository reservationRepository,
            GeneratedInventoryRepository generatedInventoryRepository,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.generatedInventoryRepository = generatedInventoryRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PagedResult<InventoryResponse> findAllInventories(
            int pageNo, int pageSize, String sortBy, String sortDir) {
        Sort sort =
                sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                        ? Sort.by(sortBy).ascending()
                        : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);
        Page<InventoryResponse> page =
                inventoryRepository.findAll(pageable).map(this::mapToInventoryResponse);
        return new PagedResult<>(page);
    }

    @Transactional(readOnly = true)
    public Optional<InventoryResponse> findInventoryById(Long id) {
        return inventoryRepository.findById(id).map(this::mapToInventoryResponse);
    }

    @Transactional(readOnly = true)
    public Optional<InventoryResponse> findInventoryByProductCode(String productCode) {
        return inventoryRepository.findByProductCode(productCode).map(this::mapToInventoryResponse);
    }

    @Transactional(readOnly = true)
    public PagedResult<InventoryResponse> getInventoryByProductCodes(
            List<String> codes, int pageNo, int pageSize, String sortBy, String sortDir) {
        Sort sort =
                sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                        ? Sort.by(sortBy).ascending()
                        : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);
        Page<InventoryResponse> page =
                inventoryRepository
                        .findByProductCodeIn(codes, pageable)
                        .map(this::mapToInventoryResponse);
        return new PagedResult<>(page);
    }

    public InventoryResponse saveInventory(InventoryRequest inventoryRequest) {
        if (inventoryRepository.existsByProductCode(inventoryRequest.productCode())) {
            throw ProductAlreadyExistsException.forProductCode(inventoryRequest.productCode());
        }
        Inventory inventory =
                new Inventory(
                        null,
                        inventoryRequest.productCode(),
                        inventoryRequest.availableQuantity(),
                        0);
        Inventory saved = inventoryRepository.save(inventory);
        LOGGER.info("Saved new inventory for product: {}", saved.getProductCode());
        return mapToInventoryResponse(saved);
    }

    public Optional<InventoryResponse> updateInventoryById(Long id, InventoryRequest request) {
        return inventoryRepository
                .findById(id)
                .map(
                        existing -> {
                            if (!existing.getProductCode().equals(request.productCode())) {
                                throw new InventoryConflictException(
                                        "Inventory product code cannot be changed");
                            }
                            existing.setAvailableQuantity(request.availableQuantity());
                            return mapToInventoryResponse(inventoryRepository.save(existing));
                        });
    }

    public Optional<InventoryResponse> updateInventoryByProductCode(
            String productCode, InventoryRequest request) {
        return inventoryRepository
                .findByProductCode(productCode)
                .map(
                        existing -> {
                            existing.setAvailableQuantity(request.availableQuantity());
                            return mapToInventoryResponse(inventoryRepository.save(existing));
                        });
    }

    public void deleteInventoryById(Long id) {
        Inventory inventory = inventoryRepository.findWithLockById(id).orElse(null);
        if (inventory == null) {
            return;
        }
        if (inventory.getReservedItems() > 0
                || reservationRepository.existsReservedByProductCode(inventory.getProductCode())) {
            throw new InventoryConflictException(
                    "Inventory with outstanding reservations cannot be deleted");
        }
        inventoryRepository.deleteById(id);
        LOGGER.info("Deleted inventory with id: {}", id);
    }

    public void updateGeneratedInventory(String idempotencyKey, Integer batchSize) {
        if (idempotencyKey == null || !generatedInventoryRepository.claim(idempotencyKey)) {
            LOGGER.info("Generation key already processed or null: {}", idempotencyKey);
            return;
        }
        int size = (batchSize != null && batchSize > 0) ? Math.min(batchSize, 10000) : 101;
        List<String> productCodes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            productCodes.add("ProductCode_" + idempotencyKey + "_" + i);
        }
        List<Inventory> matching = inventoryRepository.findByProductCodeIn(productCodes);
        for (Inventory inv : matching) {
            inv.setAvailableQuantity(RANDOM.nextInt(100) + 1);
        }
        inventoryRepository.saveAll(matching);
        LOGGER.info(
                "Updated generated inventory for key: {}, records updated: {}",
                idempotencyKey,
                matching.size());
    }

    public void initializeProductInventory(String productCode) {
        if (!inventoryRepository.existsByProductCode(productCode)) {
            Inventory inventory = new Inventory(null, productCode, 0, 0);
            inventoryRepository.save(inventory);
            LOGGER.info("Initialized zero inventory for new product: {}", productCode);
        }
    }

    public InventoryReservationResult reserve(InventoryReservationRequest request) {
        LOGGER.info("Processing reservation request for order: {}", request.orderId());

        // Step 1: Idempotency check
        Optional<InventoryReservation> existingOpt =
                reservationRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existingOpt.isPresent()) {
            return existingReservationResult(existingOpt.get(), request);
        }

        // Step 2: Lock products in deterministic ascending order to prevent deadlocks
        List<String> productCodes =
                request.items().stream()
                        .map(ReservationItem::productCode)
                        .distinct()
                        .sorted()
                        .toList();
        List<Inventory> lockedInventories =
                inventoryRepository.findWithLockByProductCodeIn(productCodes);
        // A concurrent retry may have committed while this transaction waited for stock locks.
        existingOpt = reservationRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existingOpt.isPresent()) {
            return existingReservationResult(existingOpt.get(), request);
        }
        Map<String, Long> requestedQuantities =
                request.items().stream()
                        .collect(
                                Collectors.groupingBy(
                                        ReservationItem::productCode,
                                        Collectors.summingLong(ReservationItem::quantity)));
        Map<String, Inventory> inventoryMap =
                lockedInventories.stream()
                        .collect(Collectors.toMap(Inventory::getProductCode, Function.identity()));

        // Check if all products exist
        if (inventoryMap.size() < productCodes.size()) {
            LOGGER.warn(
                    "Reservation failed: some products do not exist for order: {}",
                    request.orderId());
            return recordAndPublishReservation(
                    request,
                    InventoryReservationStatus.FAILED,
                    "Some requested products were not found in inventory");
        }

        // Step 3: Check each product against the total quantity across all request lines.
        for (Map.Entry<String, Long> item : requestedQuantities.entrySet()) {
            Inventory inventory = inventoryMap.get(item.getKey());
            if (inventory.getAvailableQuantity() < item.getValue()) {
                LOGGER.warn(
                        "Reservation failed: insufficient stock for product: {}, requested: {}, available: {}",
                        item.getKey(),
                        item.getValue(),
                        inventory.getAvailableQuantity());
                return recordAndPublishReservation(
                        request,
                        InventoryReservationStatus.FAILED,
                        "Insufficient stock for product " + item.getKey());
            }
        }

        // Step 4: Mutate quantities atomically using the same totals.
        for (Map.Entry<String, Long> item : requestedQuantities.entrySet()) {
            Inventory inventory = inventoryMap.get(item.getKey());
            int quantity =
                    item.getValue().intValue(); // Bounded by the available stock check above.
            inventory.setAvailableQuantity(inventory.getAvailableQuantity() - quantity);
            inventory.setReservedItems(Math.addExact(inventory.getReservedItems(), quantity));
        }
        inventoryRepository.saveAll(lockedInventories);

        return recordAndPublishReservation(
                request, InventoryReservationStatus.RESERVED, "Stock reserved successfully");
    }

    private InventoryReservationResult existingReservationResult(
            InventoryReservation existing, InventoryReservationRequest request) {
        List<ReservationItem> storedItems = deserializeItems(existing.getItemsJson());
        if (!Objects.equals(existing.getOrderId(), request.orderId())
                || !itemsEqual(storedItems, request.items())) {
            LOGGER.error("Idempotency conflict for key: {}", request.idempotencyKey());
            throw IdempotencyConflictException.forKey(request.idempotencyKey());
        }
        LOGGER.info(
                "Returning idempotent existing reservation result for key: {}, status: {}",
                request.idempotencyKey(),
                existing.getStatus());
        return new InventoryReservationResult(
                existing.getIdempotencyKey(),
                existing.getOrderId(),
                existing.getStatus(),
                "Idempotent reservation retrieved",
                storedItems);
    }

    public InventoryReservationResult release(String idempotencyKey) {
        LOGGER.info("Processing release for idempotency key: {}", idempotencyKey);
        InventoryReservation reservation =
                reservationRepository
                        .findByIdempotencyKey(idempotencyKey)
                        .orElseThrow(
                                () ->
                                        new InventoryNotFoundException(
                                                "Reservation with idempotency key '%s' not found"
                                                        .formatted(idempotencyKey)));

        List<ReservationItem> items = deserializeItems(reservation.getItemsJson());

        if (reservation.getStatus() == InventoryReservationStatus.RELEASED) {
            LOGGER.info("Reservation {} already released (idempotent)", idempotencyKey);
            return new InventoryReservationResult(
                    idempotencyKey,
                    reservation.getOrderId(),
                    InventoryReservationStatus.RELEASED,
                    "Reservation already released",
                    items);
        }

        if (reservation.getStatus() == InventoryReservationStatus.CONSUMED) {
            throw InvalidReservationTransitionException.forTransition(
                    reservation.getStatus(), "RELEASE");
        }

        if (reservation.getStatus() != InventoryReservationStatus.RESERVED) {
            throw InvalidReservationTransitionException.forTransition(
                    reservation.getStatus(), "RELEASE");
        }

        List<String> productCodes =
                items.stream().map(ReservationItem::productCode).distinct().sorted().toList();
        List<Inventory> locked = inventoryRepository.findWithLockByProductCodeIn(productCodes);
        Map<String, Inventory> map =
                locked.stream()
                        .collect(Collectors.toMap(Inventory::getProductCode, Function.identity()));

        for (ReservationItem item : items) {
            Inventory inv = map.get(item.productCode());
            if (inv != null) {
                inv.setReservedItems(Math.max(0, inv.getReservedItems() - item.quantity()));
                inv.setAvailableQuantity(inv.getAvailableQuantity() + item.quantity());
            }
        }
        inventoryRepository.saveAll(locked);

        reservation.setStatus(InventoryReservationStatus.RELEASED);
        reservation.setUpdatedAt(Instant.now());
        reservationRepository.save(reservation);

        LOGGER.info("Released reservation {}", idempotencyKey);
        return new InventoryReservationResult(
                idempotencyKey,
                reservation.getOrderId(),
                InventoryReservationStatus.RELEASED,
                "Reservation released successfully",
                items);
    }

    public InventoryReservationResult consume(String idempotencyKey) {
        LOGGER.info("Processing consume for idempotency key: {}", idempotencyKey);
        InventoryReservation reservation =
                reservationRepository
                        .findByIdempotencyKey(idempotencyKey)
                        .orElseThrow(
                                () ->
                                        new InventoryNotFoundException(
                                                "Reservation with idempotency key '%s' not found"
                                                        .formatted(idempotencyKey)));

        List<ReservationItem> items = deserializeItems(reservation.getItemsJson());

        if (reservation.getStatus() == InventoryReservationStatus.CONSUMED) {
            LOGGER.info("Reservation {} already consumed (idempotent)", idempotencyKey);
            return new InventoryReservationResult(
                    idempotencyKey,
                    reservation.getOrderId(),
                    InventoryReservationStatus.CONSUMED,
                    "Reservation already consumed",
                    items);
        }

        if (reservation.getStatus() == InventoryReservationStatus.RELEASED) {
            throw InvalidReservationTransitionException.forTransition(
                    reservation.getStatus(), "CONSUME");
        }

        if (reservation.getStatus() != InventoryReservationStatus.RESERVED) {
            throw InvalidReservationTransitionException.forTransition(
                    reservation.getStatus(), "CONSUME");
        }

        List<String> productCodes =
                items.stream().map(ReservationItem::productCode).distinct().sorted().toList();
        List<Inventory> locked = inventoryRepository.findWithLockByProductCodeIn(productCodes);
        Map<String, Inventory> map =
                locked.stream()
                        .collect(Collectors.toMap(Inventory::getProductCode, Function.identity()));

        for (ReservationItem item : items) {
            Inventory inv = map.get(item.productCode());
            if (inv != null) {
                inv.setReservedItems(Math.max(0, inv.getReservedItems() - item.quantity()));
            }
        }
        inventoryRepository.saveAll(locked);

        reservation.setStatus(InventoryReservationStatus.CONSUMED);
        reservation.setUpdatedAt(Instant.now());
        reservationRepository.save(reservation);

        LOGGER.info("Consumed reservation {}", idempotencyKey);
        return new InventoryReservationResult(
                idempotencyKey,
                reservation.getOrderId(),
                InventoryReservationStatus.CONSUMED,
                "Reservation consumed successfully",
                items);
    }

    @Transactional(readOnly = true)
    public Optional<InventoryReservationResult> getReservationStatus(String idempotencyKey) {
        return reservationRepository
                .findByIdempotencyKey(idempotencyKey)
                .map(
                        res ->
                                new InventoryReservationResult(
                                        res.getIdempotencyKey(),
                                        res.getOrderId(),
                                        res.getStatus(),
                                        "Reservation status retrieved",
                                        deserializeItems(res.getItemsJson())));
    }

    private InventoryReservationResult recordAndPublishReservation(
            InventoryReservationRequest request,
            InventoryReservationStatus status,
            String message) {
        String json = serializeItems(request.items());
        Instant now = Instant.now();
        InventoryReservation reservation =
                new InventoryReservation(
                        null, request.idempotencyKey(), request.orderId(), status, json, now, now);
        reservationRepository.save(reservation);

        InventoryReservationResult result =
                new InventoryReservationResult(
                        request.idempotencyKey(),
                        request.orderId(),
                        status,
                        message,
                        request.items());
        eventPublisher.publishEvent(new InventoryProcessedEvent(result));
        LOGGER.info(
                "Published InventoryProcessedEvent for orderId: {}, status: {}",
                request.orderId(),
                status);
        return result;
    }

    private InventoryResponse mapToInventoryResponse(Inventory inventory) {
        return new InventoryResponse(
                inventory.getId(),
                inventory.getProductCode(),
                inventory.getAvailableQuantity(),
                inventory.getReservedItems());
    }

    private String serializeItems(List<ReservationItem> items) {
        return objectMapper.writeValueAsString(items);
    }

    private List<ReservationItem> deserializeItems(String json) {
        return objectMapper.readValue(json, new TypeReference<List<ReservationItem>>() {});
    }

    private boolean itemsEqual(List<ReservationItem> list1, List<ReservationItem> list2) {
        if (list1.size() != list2.size()) {
            return false;
        }
        List<ReservationItem> sorted1 =
                list1.stream().sorted(Comparator.comparing(ReservationItem::productCode)).toList();
        List<ReservationItem> sorted2 =
                list2.stream().sorted(Comparator.comparing(ReservationItem::productCode)).toList();
        return Objects.equals(sorted1, sorted2);
    }
}
