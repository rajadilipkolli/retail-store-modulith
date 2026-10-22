/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.catalog.internal.application;

import com.example.retail.catalog.CatalogFacade;
import com.example.retail.catalog.internal.domain.Product;
import com.example.retail.catalog.internal.domain.ProductAlreadyExistsException;
import com.example.retail.catalog.internal.domain.ProductNotFoundException;
import com.example.retail.catalog.internal.persistence.ProductRepository;
import com.example.retail.catalog.internal.web.models.ProductRequest;
import com.example.retail.catalog.internal.web.models.ProductResponse;
import com.example.retail.inventory.InventoryFacade;
import com.example.retail.shared.catalog.ProductCreatedEvent;
import com.example.retail.shared.catalog.ProductDto;
import com.example.retail.shared.models.PagedResult;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService implements CatalogFacade {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final InventoryFacade inventoryFacade;

    public ProductService(
            ProductRepository productRepository,
            ApplicationEventPublisher eventPublisher,
            InventoryFacade inventoryFacade) {
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
        this.inventoryFacade = inventoryFacade;
    }

    // -------------------------------------------------------------------------
    // Public CatalogFacade implementation (cross-module contract)
    // -------------------------------------------------------------------------

    @Override
    public Optional<ProductDto> findProductByCode(String productCode) {
        return productRepository.findByProductCode(productCode).map(this::toDto);
    }

    @Override
    public Optional<ProductDto> findProductById(Long id) {
        return productRepository.findById(id).map(this::toDto);
    }

    @Override
    public boolean existsByProductCode(String productCode) {
        return productRepository.existsByProductCode(productCode);
    }

    @Override
    public boolean existsByProductCodes(List<String> productCodes) {
        if (productCodes == null || productCodes.isEmpty()) {
            return false;
        }
        Set<String> distinctCodes = new HashSet<>(productCodes);
        long count = productRepository.countByProductCodeIn(distinctCodes);
        return count == distinctCodes.size();
    }

    // -------------------------------------------------------------------------
    // Internal Web / API Service methods
    // -------------------------------------------------------------------------

    /**
     * Returns a page of catalog products with unknown (null) stock availability.
     *
     * @param pageNo zero-based page index
     * @param pageSize maximum number of products per page; must be positive
     * @param sortBy product property to sort by
     * @param sortDir descending for "desc" (case-insensitive), ascending otherwise
     * @throws IllegalArgumentException if the page index is negative, the page size is not
     *     positive, or the sort property is null or blank
     */
    public PagedResult<ProductResponse> findAllProducts(
            int pageNo, int pageSize, String sortBy, String sortDir) {
        Pageable pageable = createPageable(pageNo, pageSize, sortBy, sortDir);
        Page<Product> page = productRepository.findAll(pageable);
        return new PagedResult<>(page.map(this::toResponse));
    }

    /**
     * Returns the product for the web API with stock availability resolved from Inventory. Missing
     * inventory or a null or nonpositive available quantity yields false; an absent Inventory
     * facade leaves availability null. Inventory lookup failures propagate to the caller.
     *
     * @throws ProductNotFoundException if no product has the supplied ID
     */
    public ProductResponse findProductByIdInternal(Long id) {
        return productRepository
                .findById(id)
                .map(product -> toResponse(product, true))
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    /**
     * Returns the matching product, optionally resolving stock availability from Inventory.
     *
     * @param productCode product code to find
     * @param fetchInStock if {@code true}, queries {@link InventoryFacade} to determine if
     *     available quantity is positive; missing inventory or a null quantity yields false.
     *     Otherwise, or if the facade is absent, stock is returned as {@code null}
     * @throws RuntimeException if the requested inventory lookup fails
     * @throws ProductNotFoundException if no product has the supplied code
     */
    public ProductResponse findProductByProductCode(String productCode, boolean fetchInStock) {
        return productRepository
                .findByProductCode(productCode)
                .map(product -> toResponse(product, fetchInStock))
                .orElseThrow(() -> new ProductNotFoundException(productCode));
    }

    /** Checks all supplied codes using {@link #existsByProductCodes(List)} semantics. */
    public boolean productExistsByProductCodes(List<String> productCodes) {
        return existsByProductCodes(productCodes);
    }

    /**
     * Creates a product and publishes a {@link ProductCreatedEvent} within the transaction.
     *
     * @return the saved product with its assigned ID and unknown (null) stock availability
     * @throws ProductAlreadyExistsException if the code already exists or saving violates the named
     *     product-code uniqueness constraint
     * @throws DataIntegrityViolationException if an integrity failure is not recognized as a
     *     duplicate product code
     */
    @Transactional
    public ProductResponse saveProduct(ProductRequest request) {
        if (productRepository.existsByProductCode(request.productCode())) {
            throw new ProductAlreadyExistsException(request.productCode());
        }

        Product product = new Product();
        product.setProductCode(request.productCode());
        product.setProductName(request.productName());
        product.setDescription(request.description());
        product.setImageUrl(request.imageUrl());
        product.setPrice(request.price());

        Product saved = saveAndFlush(product);
        log.info("Saved product: id={}, code={}", saved.getId(), saved.getProductCode());

        // Publish event for RabbitMQ outbox publishing
        ProductCreatedEvent event =
                new ProductCreatedEvent(
                        saved.getProductCode(),
                        saved.getProductName(),
                        saved.getDescription(),
                        saved.getPrice(),
                        saved.getImageUrl());
        eventPublisher.publishEvent(event);
        log.info("Published ProductCreatedEvent for productCode={}", saved.getProductCode());

        return toResponse(saved);
    }

    /**
     * Replaces the product's editable fields with the request values, including null values.
     *
     * @return the updated product with unknown (null) stock availability
     * @throws ProductNotFoundException if no product has the supplied ID
     * @throws ProductAlreadyExistsException if saving violates the named product-code uniqueness
     *     constraint
     * @throws DataIntegrityViolationException if an integrity failure is not recognized as a
     *     duplicate product code
     */
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product existing =
                productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));

        existing.setProductCode(request.productCode());
        existing.setProductName(request.productName());
        existing.setDescription(request.description());
        existing.setImageUrl(request.imageUrl());
        existing.setPrice(request.price());

        Product updated = saveAndFlush(existing);
        log.info("Updated product: id={}, code={}", updated.getId(), updated.getProductCode());
        return toResponse(updated);
    }

    /**
     * Deletes the product and returns its former details with unknown (null) stock availability.
     *
     * @throws ProductNotFoundException if no product has the supplied ID
     */
    @Transactional
    public ProductResponse deleteProductById(Long id) {
        Product existing =
                productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));

        productRepository.delete(existing);
        log.info("Deleted product: id={}, code={}", existing.getId(), existing.getProductCode());
        return toResponse(existing);
    }

    /**
     * Returns products matching the optional name and price filters. A null or blank term is
     * ignored; otherwise its trimmed value matches a substring of product names without regard to
     * case. The inclusive price range applies only when both bounds are supplied. With no active
     * filters, returns all products. Stock availability is unknown (null).
     *
     * @param pageNo zero-based page index
     * @param pageSize maximum number of products per page; must be positive
     * @param sortBy product property to sort by
     * @param sortDir descending for "desc" (case-insensitive), ascending otherwise
     * @throws IllegalArgumentException if the page index is negative, the page size is not
     *     positive, or the sort property is null or blank
     */
    public PagedResult<ProductResponse> searchProducts(
            String term,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            int pageNo,
            int pageSize,
            String sortBy,
            String sortDir) {
        Pageable pageable = createPageable(pageNo, pageSize, sortBy, sortDir);
        boolean hasTerm = term != null && !term.trim().isEmpty();
        boolean hasPriceRange = minPrice != null && maxPrice != null;

        Page<Product> page;
        if (hasTerm && hasPriceRange) {
            page =
                    productRepository.findByProductNameContainingIgnoreCaseAndPriceBetween(
                            term.trim(), minPrice, maxPrice, pageable);
        } else if (hasTerm) {
            page = productRepository.findByProductNameContainingIgnoreCase(term.trim(), pageable);
        } else if (hasPriceRange) {
            page = productRepository.findByPriceBetween(minPrice, maxPrice, pageable);
        } else {
            page = productRepository.findAll(pageable);
        }

        return new PagedResult<>(page.map(this::toResponse));
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private Pageable createPageable(int pageNo, int pageSize, String sortBy, String sortDir) {
        Sort sort =
                "desc".equalsIgnoreCase(sortDir)
                        ? Sort.by(sortBy).descending()
                        : Sort.by(sortBy).ascending();
        return PageRequest.of(pageNo, pageSize, sort);
    }

    /**
     * Saves and flushes the product, translating only the Hibernate constraint named {@code
     * uc_product_code} into {@link ProductAlreadyExistsException}. Other integrity failures
     * propagate unchanged.
     */
    private Product saveAndFlush(Product product) {
        try {
            return productRepository.saveAndFlush(product);
        } catch (DataIntegrityViolationException ex) {
            for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraintViolation
                        && "uc_product_code".equals(constraintViolation.getConstraintName())) {
                    throw new ProductAlreadyExistsException(product.getProductCode());
                }
            }
            throw ex;
        }
    }

    /**
     * Returns product details with optional stock availability. A requested lookup yields false for
     * missing inventory or a null or nonpositive quantity; an absent facade leaves it null. Lookup
     * failures propagate.
     */
    private ProductResponse toResponse(Product product, boolean fetchInStock) {
        Boolean inStock = null;
        if (fetchInStock && inventoryFacade != null) {
            inStock =
                    inventoryFacade
                            .findInventoryByProductCode(product.getProductCode())
                            .map(
                                    inv ->
                                            inv.availableQuantity() != null
                                                    && inv.availableQuantity() > 0)
                            .orElse(false);
        }
        return new ProductResponse(
                product.getId(),
                product.getProductCode(),
                product.getProductName(),
                product.getDescription(),
                product.getImageUrl(),
                product.getPrice(),
                inStock);
    }

    /** Returns product details with unknown stock availability without querying Inventory. */
    private ProductResponse toResponse(Product product) {
        return toResponse(product, false);
    }

    private ProductDto toDto(Product product) {
        return new ProductDto(
                product.getId(),
                product.getProductCode(),
                product.getProductName(),
                product.getDescription(),
                product.getImageUrl(),
                product.getPrice());
    }
}
