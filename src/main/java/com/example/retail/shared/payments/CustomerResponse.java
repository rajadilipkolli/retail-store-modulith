/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.shared.payments;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

public record CustomerResponse(
        Long customerId,
        String name,
        String email,
        String phone,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String zipCode,
        String country,
        BigDecimal amountAvailable,
        BigDecimal amountReserved)
        implements Serializable {

    @Serial private static final long serialVersionUID = 1L;
}
