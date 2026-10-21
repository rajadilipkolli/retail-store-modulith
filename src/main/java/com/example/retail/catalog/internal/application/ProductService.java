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
import com.example.retail.catalog.internal.web.models.PagedResult;
import com.example.retail.catalog.internal.web.models.ProductRequest;
import com.example.retail.catalog.internal.web.models.ProductResponse;
import com.example.retail.shared.catalog.ProductCreatedEvent;
import com.example.retail.shared.catalog.ProductDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
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

    public ProductService(
            ProductRepository productRepository, ApplicationEventPublisher eventPublisher) {
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
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
        long count = productRepository.countByProductCodeIn(productCodes);
        return count == productCodes.size();
    }

    // -------------------------------------------------------------------------
    // Internal Web / API Service methods
    // -------------------------------------------------------------------------

    public PagedResult<ProductResponse> findAllProducts(
            int pageNo, int pageSize, String sortBy, String sortDir) {
        Pageable pageable = createPageable(pageNo, pageSize, sortBy, sortDir);
        Page<Product> page = productRepository.findAll(pageable);
        return new PagedResult<>(page.map(p -> toResponse(p, true)));
    }

    public ProductResponse findProductByIdInternal(Long id) {
        return productRepository
                .findById(id)
                .map(p -> toResponse(p, true))
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    public ProductResponse findProductByProductCode(String productCode, boolean fetchInStock) {
        return productRepository
                .findByProductCode(productCode)
                .map(p -> toResponse(p, fetchInStock))
                .orElseThrow(() -> new ProductNotFoundException(productCode));
    }

    public boolean productExistsByProductCodes(List<String> productCodes) {
        return existsByProductCodes(productCodes);
    }

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

        Product saved = productRepository.save(product);
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

        return toResponse(saved, true);
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product existing =
                productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));

        existing.setProductCode(request.productCode());
        existing.setProductName(request.productName());
        existing.setDescription(request.description());
        existing.setImageUrl(request.imageUrl());
        existing.setPrice(request.price());

        Product updated = productRepository.save(existing);
        log.info("Updated product: id={}, code={}", updated.getId(), updated.getProductCode());
        return toResponse(updated, true);
    }

    @Transactional
    public ProductResponse deleteProductById(Long id) {
        Product existing =
                productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));

        productRepository.delete(existing);
        log.info("Deleted product: id={}, code={}", existing.getId(), existing.getProductCode());
        return toResponse(existing, false);
    }

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

        return new PagedResult<>(page.map(p -> toResponse(p, true)));
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

    private ProductResponse toResponse(Product product, boolean inStock) {
        return new ProductResponse(
                product.getId(),
                product.getProductCode(),
                product.getProductName(),
                product.getDescription(),
                product.getImageUrl(),
                product.getPrice(),
                inStock);
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
