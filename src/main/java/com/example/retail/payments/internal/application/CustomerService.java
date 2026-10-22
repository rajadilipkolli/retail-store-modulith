/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.payments.internal.application;

import com.example.retail.payments.internal.domain.Customer;
import com.example.retail.payments.internal.domain.CustomerAlreadyExistsException;
import com.example.retail.payments.internal.domain.CustomerNotFoundException;
import com.example.retail.payments.internal.persistence.CustomerRepository;
import com.example.retail.shared.models.PagedResult;
import com.example.retail.shared.payments.CustomerRequest;
import com.example.retail.shared.payments.CustomerResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CustomerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public PagedResult<CustomerResponse> findAllCustomers(
            int pageNo, int pageSize, String sortBy, String sortDir) {
        Sort sort =
                sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                        ? Sort.by(sortBy).ascending()
                        : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);
        Page<CustomerResponse> page =
                customerRepository.findAll(pageable).map(this::mapToCustomerResponse);
        return new PagedResult<>(page);
    }

    @Transactional(readOnly = true)
    public Optional<CustomerResponse> findCustomerById(Long id) {
        return customerRepository.findById(id).map(this::mapToCustomerResponse);
    }

    @Transactional(readOnly = true)
    public Optional<CustomerResponse> findCustomerByEmail(String email) {
        return customerRepository.findByEmail(email).map(this::mapToCustomerResponse);
    }

    @Transactional(readOnly = true)
    public boolean validateCustomerEligibility(Long customerId) {
        return customerRepository
                .findById(customerId)
                .map(c -> c.getAmountAvailable().compareTo(BigDecimal.ZERO) > 0)
                .orElse(false);
    }

    public CustomerResponse saveCustomer(CustomerRequest request) {
        if (customerRepository.existsByEmail(request.email())) {
            throw CustomerAlreadyExistsException.forEmail(request.email());
        }

        BigDecimal available =
                request.amountAvailable() != null
                        ? request.amountAvailable().setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        Customer customer =
                new Customer(
                        null,
                        request.name(),
                        request.email(),
                        request.phone(),
                        request.addressLine1(),
                        request.addressLine2(),
                        request.city(),
                        request.state(),
                        request.zipCode(),
                        request.country(),
                        available,
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

        Customer saved = customerRepository.save(customer);
        LOGGER.info("Saved new customer with ID: {}", saved.getId());
        return mapToCustomerResponse(saved);
    }

    public Optional<CustomerResponse> updateCustomer(Long id, CustomerRequest request) {
        return customerRepository
                .findById(id)
                .map(
                        existing -> {
                            if (!existing.getEmail().equalsIgnoreCase(request.email())
                                    && customerRepository.existsByEmail(request.email())) {
                                throw CustomerAlreadyExistsException.forEmail(request.email());
                            }
                            existing.setName(request.name());
                            existing.setEmail(request.email());
                            existing.setPhone(request.phone());
                            existing.setAddressLine1(request.addressLine1());
                            existing.setAddressLine2(request.addressLine2());
                            existing.setCity(request.city());
                            existing.setState(request.state());
                            existing.setZipCode(request.zipCode());
                            existing.setCountry(request.country());
                            if (request.amountAvailable() != null) {
                                existing.setAmountAvailable(
                                        request.amountAvailable()
                                                .setScale(2, RoundingMode.HALF_UP));
                            }
                            return mapToCustomerResponse(customerRepository.save(existing));
                        });
    }

    public void deleteCustomerById(Long id) {
        if (!customerRepository.existsById(id)) {
            throw new CustomerNotFoundException(id);
        }
        customerRepository.deleteById(id);
        LOGGER.info("Deleted customer with ID: {}", id);
    }

    private CustomerResponse mapToCustomerResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getAddressLine1(),
                customer.getAddressLine2(),
                customer.getCity(),
                customer.getState(),
                customer.getZipCode(),
                customer.getCountry(),
                customer.getAmountAvailable(),
                customer.getAmountReserved());
    }
}
