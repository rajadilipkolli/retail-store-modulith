/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.catalog;

import com.example.retail.shared.catalog.ProductDto;
import java.util.List;
import java.util.Optional;

/**
 * Public API facade for the Catalog module. Other modules (such as Orders) access catalog
 * functionality through this interface.
 */
public interface CatalogFacade {

    /** Returns the matching product, or an empty result when the code is not found. */
    Optional<ProductDto> findProductByCode(String productCode);

    /** Returns the matching product, or an empty result when the ID is not found. */
    Optional<ProductDto> findProductById(Long id);

    /** Checks whether a catalog product exists for the supplied code. */
    boolean existsByProductCode(String productCode);

    /**
     * Checks whether every distinct supplied code exists in the catalog. Duplicate codes are
     * ignored; a null or empty list returns false.
     */
    boolean existsByProductCodes(List<String> productCodes);
}
