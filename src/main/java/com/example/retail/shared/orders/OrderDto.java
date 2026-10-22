/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.shared.orders;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import org.springframework.modulith.events.Externalized;

@Externalized("orders.exchange::order.created")
public record OrderDto(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long orderId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
                @Positive(message = "CustomerId should be positive")
                Long customerId,
        String status,
        String source,
        @NotEmpty(message = "Order without items not valid")
                List<@NotNull @Valid OrderItemDto> items,
        String attemptId)
        implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    public OrderDto(
            Long orderId, Long customerId, String status, String source, List<OrderItemDto> items) {
        this(orderId, customerId, status, source, items, null);
    }

    public OrderDto withAttemptId(String attemptId) {
        return new OrderDto(orderId(), customerId(), status(), source(), items(), attemptId);
    }

    public OrderDto withStatusAndSource(String status, String source) {
        if (Objects.equals(this.status(), status) && Objects.equals(this.source(), source)) {
            return this;
        }
        return new OrderDto(orderId(), customerId(), status(), source(), items(), attemptId());
    }

    public record OrderItemDto(
            @JsonFormat(shape = JsonFormat.Shape.STRING) Long itemId,
            String productId,
            @NotNull(message = "Quantity cannot be null")
                    @Positive(message = "Quantity should be positive")
                    Integer quantity,
            BigDecimal productPrice)
            implements Serializable {

        @Serial private static final long serialVersionUID = 1L;

        public BigDecimal getPrice() {
            return this.productPrice()
                    .multiply(BigDecimal.valueOf(this.quantity()))
                    .setScale(2, RoundingMode.HALF_UP);
        }
    }
}
