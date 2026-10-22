/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.shared.payments;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

public record CustomerRequest(
        @NotBlank(message = "Name cannot be blank") String name,
        @NotBlank(message = "Email cannot be blank") @Email(message = "Email must be valid")
                String email,
        @NotBlank(message = "Phone cannot be blank") String phone,
        @NotBlank(message = "Address line 1 cannot be blank") String addressLine1,
        String addressLine2,
        @NotBlank(message = "City cannot be blank") String city,
        @NotBlank(message = "State cannot be blank") String state,
        @NotBlank(message = "Zip code cannot be blank") String zipCode,
        @NotBlank(message = "Country cannot be blank") String country,
        @PositiveOrZero(message = "Amount available must be non-negative")
                BigDecimal amountAvailable)
        implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    public CustomerRequest(
            String name,
            String email,
            String phone,
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String zipCode,
            String country) {
        this(
                name,
                email,
                phone,
                addressLine1,
                addressLine2,
                city,
                state,
                zipCode,
                country,
                BigDecimal.ZERO);
    }
}
