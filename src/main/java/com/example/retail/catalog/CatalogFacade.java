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

    Optional<ProductDto> findProductByCode(String productCode);

    Optional<ProductDto> findProductById(Long id);

    boolean existsByProductCode(String productCode);

    boolean existsByProductCodes(List<String> productCodes);
}
