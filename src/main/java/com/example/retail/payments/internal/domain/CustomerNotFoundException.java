/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.payments.internal.domain;

public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(Long id) {
        super("Customer with id '%d' not found".formatted(id));
    }

    public CustomerNotFoundException(String message) {
        super(message);
    }

    public static CustomerNotFoundException forEmail(String email) {
        return new CustomerNotFoundException("Customer with email '%s' not found".formatted(email));
    }
}
