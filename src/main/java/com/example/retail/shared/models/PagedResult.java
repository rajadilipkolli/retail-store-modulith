/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.shared.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import org.springframework.data.domain.Page;

public record PagedResult<T>(
        List<T> data,
        Long totalElements,
        Integer pageNumber,
        Integer totalPages,
        @JsonProperty("isFirst") Boolean isFirst,
        @JsonProperty("isLast") Boolean isLast,
        @JsonProperty("hasNext") Boolean hasNext,
        @JsonProperty("hasPrevious") Boolean hasPrevious)
        implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** Copies page contents and navigation metadata, preserving the zero-based page number. */
    public PagedResult(Page<T> page) {
        this(
                page.getContent(),
                page.getTotalElements(),
                page.getNumber(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.hasNext(),
                page.hasPrevious());
    }
}
