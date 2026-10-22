/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.example.retail.payments.internal.application.CustomerService;
import com.example.retail.payments.internal.domain.CustomerAlreadyExistsException;
import com.example.retail.payments.internal.web.CustomerController;
import com.example.retail.payments.internal.web.PaymentExceptionHandler;
import com.example.retail.shared.models.PagedResult;
import com.example.retail.shared.payments.CustomerRequest;
import com.example.retail.shared.payments.CustomerResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(controllers = CustomerController.class)
@Import(PaymentExceptionHandler.class)
class CustomerControllerTest {

    @Autowired private MockMvcTester mockMvcTester;

    @MockitoBean private CustomerService customerService;

    private CustomerResponse createSampleResponse(Long id, String email) {
        return new CustomerResponse(
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
                BigDecimal.valueOf(100.00).setScale(2),
                BigDecimal.ZERO.setScale(2));
    }

    @Test
    @DisplayName("GET /api/v1/customers returns paged customers")
    void getAllCustomers() {
        CustomerResponse res = createSampleResponse(1L, "john@example.com");
        PagedResult<CustomerResponse> paged = new PagedResult<>(new PageImpl<>(List.of(res)));
        given(customerService.findAllCustomers(0, 10, "id", "asc")).willReturn(paged);

        assertThat(mockMvcTester.get().uri("/api/v1/customers"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data")
                .asArray()
                .hasSize(1);
    }

    @Test
    @DisplayName("GET /api/v1/customers?email=... filters by email")
    void getCustomersByEmailParam() {
        CustomerResponse res = createSampleResponse(1L, "john@example.com");
        given(customerService.findCustomerByEmail("john@example.com")).willReturn(Optional.of(res));

        assertThat(mockMvcTester.get().uri("/api/v1/customers?email=john@example.com"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data[0].email")
                .isEqualTo("john@example.com");
    }

    @Test
    @DisplayName("GET /api/v1/customers/{id} returns customer when found")
    void getCustomerByIdFound() {
        CustomerResponse res = createSampleResponse(1L, "john@example.com");
        given(customerService.findCustomerById(1L)).willReturn(Optional.of(res));

        assertThat(mockMvcTester.get().uri("/api/v1/customers/1"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.customerId")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("GET /api/v1/customers/{id} returns 404 when missing")
    void getCustomerByIdNotFound() {
        given(customerService.findCustomerById(999L)).willReturn(Optional.empty());

        assertThat(mockMvcTester.get().uri("/api/v1/customers/999"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET /api/v1/customers/email/{email} returns customer when found")
    void getCustomerByEmailFound() {
        CustomerResponse res = createSampleResponse(1L, "john@example.com");
        given(customerService.findCustomerByEmail("john@example.com")).willReturn(Optional.of(res));

        assertThat(mockMvcTester.get().uri("/api/v1/customers/email/john@example.com"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.email")
                .isEqualTo("john@example.com");
    }

    @Test
    @DisplayName("GET /api/v1/customers/email/{email} returns 404 when missing")
    void getCustomerByEmailNotFound() {
        given(customerService.findCustomerByEmail("missing@example.com"))
                .willReturn(Optional.empty());

        assertThat(mockMvcTester.get().uri("/api/v1/customers/email/missing@example.com"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("POST /api/v1/customers creates customer with 201 Created and Location header")
    void createCustomerSuccess() {
        CustomerResponse res = createSampleResponse(42L, "new@example.com");
        given(customerService.saveCustomer(any(CustomerRequest.class))).willReturn(res);

        String json =
                """
                {
                    "name": "Jane Doe",
                    "email": "new@example.com",
                    "phone": "1234567890",
                    "addressLine1": "789 Pine St",
                    "city": "Metropolis",
                    "state": "NY",
                    "zipCode": "10001",
                    "country": "USA",
                    "amountAvailable": 250.00
                }
                """;

        assertThat(
                        mockMvcTester
                                .post()
                                .uri("/api/v1/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "/api/v1/customers/42")
                .bodyJson()
                .extractingPath("$.customerId")
                .isEqualTo(42);
    }

    @Test
    @DisplayName("POST /api/v1/customers returns 409 Conflict when email already exists")
    void createCustomerDuplicateEmailThrows409() {
        given(customerService.saveCustomer(any(CustomerRequest.class)))
                .willThrow(CustomerAlreadyExistsException.forEmail("dup@example.com"));

        String json =
                """
                {
                    "name": "Jane Doe",
                    "email": "dup@example.com",
                    "phone": "1234567890",
                    "addressLine1": "789 Pine St",
                    "city": "Metropolis",
                    "state": "NY",
                    "zipCode": "10001",
                    "country": "USA",
                    "amountAvailable": 100.00
                }
                """;

        assertThat(
                        mockMvcTester
                                .post()
                                .uri("/api/v1/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("POST /api/v1/customers returns 400 Bad Request on validation failure")
    void createCustomerValidationFailure() {
        String invalidJson =
                """
                {
                    "name": "",
                    "email": "not-an-email"
                }
                """;

        assertThat(
                        mockMvcTester
                                .post()
                                .uri("/api/v1/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidJson))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("PUT /api/v1/customers/{id} updates customer successfully")
    void updateCustomerSuccess() {
        CustomerResponse res = createSampleResponse(1L, "john@example.com");
        given(customerService.updateCustomer(eq(1L), any(CustomerRequest.class)))
                .willReturn(Optional.of(res));

        String json =
                """
                {
                    "name": "John Doe",
                    "email": "john@example.com",
                    "phone": "1234567890",
                    "addressLine1": "123 Main St",
                    "city": "Metropolis",
                    "state": "NY",
                    "zipCode": "10001",
                    "country": "USA",
                    "amountAvailable": 100.00
                }
                """;

        assertThat(
                        mockMvcTester
                                .put()
                                .uri("/api/v1/customers/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.customerId")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("PUT /api/v1/customers/{id} returns 404 when missing")
    void updateCustomerNotFound() {
        given(customerService.updateCustomer(eq(999L), any(CustomerRequest.class)))
                .willReturn(Optional.empty());

        String json =
                """
                {
                    "name": "John Doe",
                    "email": "john@example.com",
                    "phone": "1234567890",
                    "addressLine1": "123 Main St",
                    "city": "Metropolis",
                    "state": "NY",
                    "zipCode": "10001",
                    "country": "USA",
                    "amountAvailable": 100.00
                }
                """;

        assertThat(
                        mockMvcTester
                                .put()
                                .uri("/api/v1/customers/999")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("DELETE /api/v1/customers/{id} deletes and returns former details")
    void deleteCustomerSuccess() {
        CustomerResponse res = createSampleResponse(1L, "john@example.com");
        given(customerService.findCustomerById(1L)).willReturn(Optional.of(res));

        assertThat(mockMvcTester.delete().uri("/api/v1/customers/1"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.customerId")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("DELETE /api/v1/customers/{id} returns 404 when missing")
    void deleteCustomerNotFound() {
        given(customerService.findCustomerById(999L)).willReturn(Optional.empty());

        assertThat(mockMvcTester.delete().uri("/api/v1/customers/999"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }
}
