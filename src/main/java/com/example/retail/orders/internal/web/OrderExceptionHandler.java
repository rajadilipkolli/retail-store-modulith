/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.orders.internal.web;

import com.example.retail.orders.internal.domain.IdempotencyConflictException;
import com.example.retail.orders.internal.domain.OrderNotFoundException;
import java.net.URI;
import java.time.Instant;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.example.retail.orders.internal.web")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OrderExceptionHandler {

    /** Returns an HTTP 404 problem detail with the exception message and current timestamp. */
    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail handleOrderNotFound(OrderNotFoundException ex) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Order Not Found");
        problem.setType(URI.create("https://api.retail.example.com/errors/order-not-found"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /** Returns an HTTP 409 problem detail with the exception message and current timestamp. */
    @ExceptionHandler(IdempotencyConflictException.class)
    public ProblemDetail handleIdempotencyConflict(IdempotencyConflictException ex) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Idempotency Conflict");
        problem.setType(URI.create("https://api.retail.example.com/errors/idempotency-conflict"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /** Returns an HTTP 400 problem detail with the exception message and current timestamp. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        problem.setType(URI.create("https://api.retail.example.com/errors/bad-request"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
