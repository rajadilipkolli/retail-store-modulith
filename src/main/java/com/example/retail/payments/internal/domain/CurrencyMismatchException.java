/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.payments.internal.domain;

public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(String currency) {
        super(
                "Unsupported currency or currency mismatch: '%s'. Supported currency is 'USD'"
                        .formatted(currency));
    }
}
