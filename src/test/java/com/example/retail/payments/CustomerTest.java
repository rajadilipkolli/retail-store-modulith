/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.retail.payments.internal.domain.Customer;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CustomerTest {

    @Test
    @DisplayName("Customer getters, setters, and constructors operate correctly")
    void customerEntityAccessors() {
        Customer customer = new Customer();
        customer.setId(101L);
        customer.setName("John Doe");
        customer.setEmail("john@example.com");
        customer.setPhone("12345");
        customer.setAddressLine1("Street 1");
        customer.setAddressLine2("Apt 2");
        customer.setCity("Metropolis");
        customer.setState("NY");
        customer.setZipCode("10001");
        customer.setCountry("USA");
        customer.setAmountAvailable(BigDecimal.valueOf(500.00));
        customer.setAmountReserved(BigDecimal.valueOf(50.00));
        customer.setVersion(1);

        assertThat(customer.getId()).isEqualTo(101L);
        assertThat(customer.getName()).isEqualTo("John Doe");
        assertThat(customer.getEmail()).isEqualTo("john@example.com");
        assertThat(customer.getPhone()).isEqualTo("12345");
        assertThat(customer.getAddressLine1()).isEqualTo("Street 1");
        assertThat(customer.getAddressLine2()).isEqualTo("Apt 2");
        assertThat(customer.getCity()).isEqualTo("Metropolis");
        assertThat(customer.getState()).isEqualTo("NY");
        assertThat(customer.getZipCode()).isEqualTo("10001");
        assertThat(customer.getCountry()).isEqualTo("USA");
        assertThat(customer.getAmountAvailable()).isEqualTo(BigDecimal.valueOf(500.00));
        assertThat(customer.getAmountReserved()).isEqualTo(BigDecimal.valueOf(50.00));
        assertThat(customer.getVersion()).isEqualTo(1);

        Customer parameterized =
                new Customer(
                        102L,
                        "Jane",
                        "jane@example.com",
                        "67890",
                        "Street 2",
                        null,
                        "Gotham",
                        "NJ",
                        "07001",
                        "USA",
                        null,
                        null);
        assertThat(parameterized.getAmountAvailable()).isEqualTo(BigDecimal.ZERO);
        assertThat(parameterized.getAmountReserved()).isEqualTo(BigDecimal.ZERO);
    }
}
