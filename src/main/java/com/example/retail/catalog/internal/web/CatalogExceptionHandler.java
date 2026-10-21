/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.catalog.internal.web;

import com.example.retail.catalog.internal.domain.ProductAlreadyExistsException;
import com.example.retail.catalog.internal.domain.ProductNotFoundException;
import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = ProductController.class)
public class CatalogExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleProductNotFound(ProductNotFoundException ex) {
        return ex.getBody();
    }

    @ExceptionHandler(ProductAlreadyExistsException.class)
    public ProblemDetail handleProductAlreadyExists(ProductAlreadyExistsException ex) {
        return ex.getBody();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.BAD_REQUEST, "Invalid request content.");
        problemDetail.setTitle("Constraint Violation");
        problemDetail.setType(URI.create("https://api.microservices.com/errors/bad-request"));
        problemDetail.setProperty("timestamp", Instant.now());

        List<ViolationDetail> violations =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(
                                error ->
                                        new ViolationDetail(
                                                error.getField(), error.getDefaultMessage()))
                        .sorted(Comparator.comparing(ViolationDetail::field))
                        .toList();

        problemDetail.setProperty("violations", violations);
        return problemDetail;
    }

    public record ViolationDetail(String field, String message) {}
}
