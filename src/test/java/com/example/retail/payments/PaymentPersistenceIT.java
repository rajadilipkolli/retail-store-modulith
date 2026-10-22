/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.retail.catalog.ContainersConfig;
import com.example.retail.payments.internal.domain.Customer;
import com.example.retail.payments.internal.domain.PaymentOperation;
import com.example.retail.payments.internal.persistence.CustomerRepository;
import com.example.retail.payments.internal.persistence.PaymentOperationRepository;
import com.example.retail.shared.payments.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest(properties = "spring.liquibase.contexts=sample-data")
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
class PaymentPersistenceIT {

    @Autowired private MockMvcTester mockMvcTester;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private PaymentOperationRepository operationRepository;

    @Test
    @DisplayName("Sample customers from Liquibase are accessible via /api/v1/customers")
    void sampleCustomersLoadedAndAccessible() {
        var result = mockMvcTester.get().uri("/api/v1/customers");
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.totalElements")
                .asNumber()
                .satisfies(n -> assertThat(n.intValue()).isGreaterThanOrEqualTo(5));
    }

    @Test
    @DisplayName("Sample customers have non-negative balances")
    void sampleCustomersHaveValidBalances() {
        Long invalidCount =
                jdbcClient
                        .sql(
                                "SELECT count(*) FROM payment.customers WHERE amount_available < 0 OR amount_reserved < 0")
                        .query(Long.class)
                        .single();
        assertThat(invalidCount).isZero();
    }

    @Test
    @DisplayName("Customer entity generates TSID primary key on persist")
    void customerTsidGenerated() {
        Customer customer =
                new Customer(
                        null,
                        "TSID Test",
                        "tsid_test@example.com",
                        "1234567890",
                        "123 St",
                        null,
                        "City",
                        "State",
                        "12345",
                        "USA",
                        BigDecimal.valueOf(100.00).setScale(2),
                        BigDecimal.ZERO.setScale(2));

        Customer saved = customerRepository.save(customer);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId()).isPositive();
    }

    @Test
    @DisplayName("Database enforces check constraint amount_available >= 0")
    void databaseCheckConstraintAmountAvailable() {
        assertThatThrownBy(
                        () ->
                                jdbcClient
                                        .sql(
                                                "INSERT INTO payment.customers (id, name, email, phone, address_line1, city, state, zip_code, country, amount_available, amount_reserved, version) VALUES (999991, 'Bad', 'bad1@test.com', '1234567890', '123 St', 'City', 'State', '12345', 'USA', -10.00, 0.00, 0)")
                                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("chk_customers_amount_available");
    }

    @Test
    @DisplayName("Database enforces check constraint amount_reserved >= 0")
    void databaseCheckConstraintAmountReserved() {
        assertThatThrownBy(
                        () ->
                                jdbcClient
                                        .sql(
                                                "INSERT INTO payment.customers (id, name, email, phone, address_line1, city, state, zip_code, country, amount_available, amount_reserved, version) VALUES (999992, 'Bad', 'bad2@test.com', '1234567890', '123 St', 'City', 'State', '12345', 'USA', 10.00, -5.00, 0)")
                                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("chk_customers_amount_reserved");
    }

    @Test
    @DisplayName("Database enforces unique constraint on idempotency_key in payment_operations")
    void databaseUniqueConstraintIdempotencyKey() {
        PaymentOperation op1 =
                new PaymentOperation(
                        "uc-key-1",
                        101L,
                        101L,
                        "RESERVE",
                        BigDecimal.valueOf(10.00).setScale(2),
                        "USD",
                        PaymentStatus.RESERVED,
                        null,
                        Instant.now());
        operationRepository.saveAndFlush(op1);

        PaymentOperation op2 =
                new PaymentOperation(
                        "uc-key-1",
                        102L,
                        101L,
                        "RESERVE",
                        BigDecimal.valueOf(20.00).setScale(2),
                        "USD",
                        PaymentStatus.RESERVED,
                        null,
                        Instant.now());

        assertThatThrownBy(() -> operationRepository.saveAndFlush(op2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
