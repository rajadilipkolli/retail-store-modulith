/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.retail.payments.internal.application.CustomerService;
import com.example.retail.payments.internal.domain.Customer;
import com.example.retail.payments.internal.domain.CustomerAlreadyExistsException;
import com.example.retail.payments.internal.domain.CustomerNotFoundException;
import com.example.retail.payments.internal.persistence.CustomerRepository;
import com.example.retail.shared.models.PagedResult;
import com.example.retail.shared.payments.CustomerRequest;
import com.example.retail.shared.payments.CustomerResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock private CustomerRepository customerRepository;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(customerRepository);
    }

    private Customer createCustomerFixture(Long id, String email, BigDecimal available) {
        return new Customer(
                id,
                "John Doe",
                email,
                "1234567890",
                "123 Main St",
                "Apt 4B",
                "Metropolis",
                "NY",
                "10001",
                "USA",
                available,
                BigDecimal.ZERO.setScale(2));
    }

    @Test
    @DisplayName("findAllCustomers returns paged result")
    void findAllCustomers() {
        Customer c = createCustomerFixture(1L, "john@example.com", BigDecimal.valueOf(100.00));
        given(customerRepository.findAll(any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(c)));

        PagedResult<CustomerResponse> result = customerService.findAllCustomers(0, 10, "id", "asc");

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().getFirst().email()).isEqualTo("john@example.com");
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("findCustomerById returns customer when present")
    void findCustomerByIdFound() {
        Customer c = createCustomerFixture(1L, "john@example.com", BigDecimal.valueOf(100.00));
        given(customerRepository.findById(1L)).willReturn(Optional.of(c));

        Optional<CustomerResponse> result = customerService.findCustomerById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().customerId()).isEqualTo(1L);
        assertThat(result.get().name()).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("findCustomerById returns empty when missing")
    void findCustomerByIdNotFound() {
        given(customerRepository.findById(999L)).willReturn(Optional.empty());

        Optional<CustomerResponse> result = customerService.findCustomerById(999L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findCustomerByEmail returns customer when present")
    void findCustomerByEmailFound() {
        Customer c = createCustomerFixture(1L, "john@example.com", BigDecimal.valueOf(100.00));
        given(customerRepository.findByEmail("john@example.com")).willReturn(Optional.of(c));

        Optional<CustomerResponse> result = customerService.findCustomerByEmail("john@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().email()).isEqualTo("john@example.com");
    }

    @Test
    @DisplayName("findCustomerByEmail returns empty when missing")
    void findCustomerByEmailNotFound() {
        given(customerRepository.findByEmail("missing@example.com")).willReturn(Optional.empty());

        Optional<CustomerResponse> result =
                customerService.findCustomerByEmail("missing@example.com");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("validateCustomerEligibility returns true when available balance > 0")
    void validateCustomerEligibilityTrue() {
        Customer c = createCustomerFixture(1L, "john@example.com", BigDecimal.valueOf(50.00));
        given(customerRepository.findById(1L)).willReturn(Optional.of(c));

        boolean eligible = customerService.validateCustomerEligibility(1L);

        assertThat(eligible).isTrue();
    }

    @Test
    @DisplayName("validateCustomerEligibility returns false when available balance is zero")
    void validateCustomerEligibilityZeroBalance() {
        Customer c = createCustomerFixture(1L, "john@example.com", BigDecimal.ZERO);
        given(customerRepository.findById(1L)).willReturn(Optional.of(c));

        boolean eligible = customerService.validateCustomerEligibility(1L);

        assertThat(eligible).isFalse();
    }

    @Test
    @DisplayName("validateCustomerEligibility returns false when customer is missing")
    void validateCustomerEligibilityNotFound() {
        given(customerRepository.findById(999L)).willReturn(Optional.empty());

        boolean eligible = customerService.validateCustomerEligibility(999L);

        assertThat(eligible).isFalse();
    }

    @Test
    @DisplayName("saveCustomer successfully saves customer with scaled balance")
    void saveCustomerSuccess() {
        CustomerRequest request =
                new CustomerRequest(
                        "Alice Smith",
                        "alice@example.com",
                        "9876543210",
                        "456 Elm St",
                        null,
                        "Gotham",
                        "NJ",
                        "07001",
                        "USA",
                        BigDecimal.valueOf(123.456));

        given(customerRepository.existsByEmail("alice@example.com")).willReturn(false);
        given(customerRepository.save(any(Customer.class)))
                .willAnswer(
                        inv -> {
                            Customer c = inv.getArgument(0);
                            return new Customer(
                                    2L,
                                    c.getName(),
                                    c.getEmail(),
                                    c.getPhone(),
                                    c.getAddressLine1(),
                                    c.getAddressLine2(),
                                    c.getCity(),
                                    c.getState(),
                                    c.getZipCode(),
                                    c.getCountry(),
                                    c.getAmountAvailable(),
                                    c.getAmountReserved());
                        });

        CustomerResponse response = customerService.saveCustomer(request);

        assertThat(response.customerId()).isEqualTo(2L);
        assertThat(response.email()).isEqualTo("alice@example.com");
        // Check canonical money rounding: 123.456 -> 123.46
        assertThat(response.amountAvailable()).isEqualByComparingTo("123.46");
        assertThat(response.amountReserved()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("saveCustomer throws CustomerAlreadyExistsException when email taken")
    void saveCustomerDuplicateEmailThrows() {
        CustomerRequest request =
                new CustomerRequest(
                        "Alice",
                        "alice@example.com",
                        "123",
                        "Street",
                        null,
                        "City",
                        "State",
                        "12345",
                        "USA",
                        BigDecimal.valueOf(100));

        given(customerRepository.existsByEmail("alice@example.com")).willReturn(true);

        assertThatThrownBy(() -> customerService.saveCustomer(request))
                .isInstanceOf(CustomerAlreadyExistsException.class)
                .hasMessageContaining("alice@example.com");

        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateCustomer updates details successfully")
    void updateCustomerSuccess() {
        Customer existing =
                createCustomerFixture(1L, "john@example.com", BigDecimal.valueOf(100.00));
        given(customerRepository.findById(1L)).willReturn(Optional.of(existing));
        given(customerRepository.save(any(Customer.class))).willAnswer(inv -> inv.getArgument(0));

        CustomerRequest updateReq =
                new CustomerRequest(
                        "John Updated",
                        "john@example.com",
                        "1112223333",
                        "New St",
                        null,
                        "New City",
                        "NY",
                        "10002",
                        "USA",
                        BigDecimal.valueOf(250.00));

        Optional<CustomerResponse> updated = customerService.updateCustomer(1L, updateReq);

        assertThat(updated).isPresent();
        assertThat(updated.get().name()).isEqualTo("John Updated");
        assertThat(updated.get().phone()).isEqualTo("1112223333");
        assertThat(updated.get().amountAvailable()).isEqualByComparingTo("250.00");
    }

    @Test
    @DisplayName("updateCustomer with taken email throws CustomerAlreadyExistsException")
    void updateCustomerDuplicateEmailThrows() {
        Customer existing =
                createCustomerFixture(1L, "john@example.com", BigDecimal.valueOf(100.00));
        given(customerRepository.findById(1L)).willReturn(Optional.of(existing));
        given(customerRepository.existsByEmail("taken@example.com")).willReturn(true);

        CustomerRequest updateReq =
                new CustomerRequest(
                        "John",
                        "taken@example.com",
                        "111",
                        "St",
                        null,
                        "City",
                        "NY",
                        "10001",
                        "USA",
                        BigDecimal.valueOf(100));

        assertThatThrownBy(() -> customerService.updateCustomer(1L, updateReq))
                .isInstanceOf(CustomerAlreadyExistsException.class);
    }

    @Test
    @DisplayName("deleteCustomerById deletes when present")
    void deleteCustomerSuccess() {
        given(customerRepository.existsById(1L)).willReturn(true);

        customerService.deleteCustomerById(1L);

        verify(customerRepository).deleteById(1L);
    }

    @Test
    @DisplayName("deleteCustomerById throws CustomerNotFoundException when missing")
    void deleteCustomerNotFoundThrows() {
        given(customerRepository.existsById(999L)).willReturn(false);

        assertThatThrownBy(() -> customerService.deleteCustomerById(999L))
                .isInstanceOf(CustomerNotFoundException.class);

        verify(customerRepository, never()).deleteById(any());
    }
}
