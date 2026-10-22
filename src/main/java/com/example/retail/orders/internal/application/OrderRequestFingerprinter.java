/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders.internal.application;

import com.example.retail.shared.orders.Address;
import com.example.retail.shared.orders.OrderItemRequest;
import com.example.retail.shared.orders.OrderRequest;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class OrderRequestFingerprinter {

    public static final String FINGERPRINT_VERSION = "v1";

    public String computeFingerprint(OrderRequest request) {
        Objects.requireNonNull(request, "OrderRequest must not be null");

        // 1. Normalize items sorted by productCode
        List<OrderItemRequest> sortedItems =
                request.items() == null
                        ? List.of()
                        : request.items().stream()
                                .filter(Objects::nonNull)
                                .sorted(
                                        Comparator.comparing(
                                                item -> item.productCode().trim().toLowerCase()))
                                .toList();

        String normalizedItems =
                sortedItems.stream()
                        .map(
                                item ->
                                        item.productCode().trim().toLowerCase()
                                                + ":"
                                                + item.quantity()
                                                + ":"
                                                + (item.productPrice() != null
                                                        ? item.productPrice()
                                                                .setScale(2, RoundingMode.HALF_UP)
                                                        : "0.00"))
                        .collect(Collectors.joining(","));

        // 2. Normalize delivery address
        String normalizedAddress = normalizeAddress(request.deliveryAddress());

        // 3. Construct canonical representation
        String canonicalString =
                FINGERPRINT_VERSION
                        + "|customerId="
                        + request.customerId()
                        + "|items="
                        + normalizedItems
                        + "|address="
                        + normalizedAddress;

        return sha256Hex(canonicalString);
    }

    private String normalizeAddress(Address address) {
        if (address == null) {
            return "none";
        }
        return safeTrimLower(address.addressLine1())
                + "|"
                + safeTrimLower(address.addressLine2())
                + "|"
                + safeTrimLower(address.city())
                + "|"
                + safeTrimLower(address.state())
                + "|"
                + safeTrimLower(address.zipCode())
                + "|"
                + safeTrimLower(address.country());
    }

    private String safeTrimLower(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
