/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.payments.internal.web;

import com.example.retail.payments.internal.application.CustomerService;
import com.example.retail.shared.models.PagedResult;
import com.example.retail.shared.payments.CustomerRequest;
import com.example.retail.shared.payments.CustomerResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
@Validated
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    /**
     * Returns HTTP 200 with a customer page. A nonblank email selects an untrimmed email lookup and
     * ignores pagination and sorting, returning a singleton or empty page. Otherwise, page numbers
     * are zero-based and sorting follows {@link CustomerService#findAllCustomers(int, int, String,
     * String)}.
     */
    @GetMapping
    public ResponseEntity<PagedResult<CustomerResponse>> getAllCustomers(
            @RequestParam(required = false) String email,
            @RequestParam(defaultValue = "0", required = false) int pageNo,
            @RequestParam(defaultValue = "10", required = false) int pageSize,
            @RequestParam(defaultValue = "id", required = false) String sortBy,
            @RequestParam(defaultValue = "asc", required = false) String sortDir) {
        if (email != null && !email.trim().isEmpty()) {
            return customerService
                    .findCustomerByEmail(email)
                    .map(c -> ResponseEntity.ok(new PagedResult<>(new PageImpl<>(List.of(c)))))
                    .orElseGet(
                            () -> ResponseEntity.ok(new PagedResult<>(new PageImpl<>(List.of()))));
        }
        return ResponseEntity.ok(
                customerService.findAllCustomers(pageNo, pageSize, sortBy, sortDir));
    }

    /** Returns HTTP 200 with customer details, or HTTP 404 if the ID is unknown. */
    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getCustomerById(@PathVariable Long id) {
        return customerService
                .findCustomerById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Returns HTTP 200 with customer details, or HTTP 404 if the email is unknown. */
    @GetMapping("/email/{email}")
    public ResponseEntity<CustomerResponse> getCustomerByEmail(@PathVariable String email) {
        return customerService
                .findCustomerByEmail(email)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Creates a customer and returns HTTP 201 with saved details and the customer resource
     * location.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<CustomerResponse> createCustomer(
            @RequestBody @Valid CustomerRequest customerRequest) {
        CustomerResponse response = customerService.saveCustomer(customerRequest);
        return ResponseEntity.created(URI.create("/api/v1/customers/" + response.customerId()))
                .body(response);
    }

    /**
     * Updates customer details and returns HTTP 200 with the result, or HTTP 404 for an unknown ID.
     */
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CustomerResponse> updateCustomer(
            @PathVariable Long id, @RequestBody @Valid CustomerRequest customerRequest) {
        return customerService
                .updateCustomer(id, customerRequest)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Deletes the customer and returns HTTP 200 with the former details, or HTTP 404 if absent. */
    @DeleteMapping("/{id}")
    public ResponseEntity<CustomerResponse> deleteCustomer(@PathVariable Long id) {
        return customerService
                .findCustomerById(id)
                .map(
                        customer -> {
                            customerService.deleteCustomerById(id);
                            return ResponseEntity.ok(customer);
                        })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
