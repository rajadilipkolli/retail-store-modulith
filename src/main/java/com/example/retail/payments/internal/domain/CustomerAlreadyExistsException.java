/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.payments.internal.domain;

public class CustomerAlreadyExistsException extends RuntimeException {

    public CustomerAlreadyExistsException(String message) {
        super(message);
    }

    public static CustomerAlreadyExistsException forEmail(String email) {
        return new CustomerAlreadyExistsException(
                "Customer with email '%s' already exists".formatted(email));
    }
}
