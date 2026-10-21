/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.catalog.internal.web;

import com.example.retail.catalog.internal.application.ProductService;
import com.example.retail.catalog.internal.web.models.PagedResult;
import com.example.retail.catalog.internal.web.models.ProductExistsResponse;
import com.example.retail.catalog.internal.web.models.ProductRequest;
import com.example.retail.catalog.internal.web.models.ProductResponse;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Set;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping({"/api/v1/catalog"})
@Validated
public class ProductController {

    private static final Set<String> SUPPORTED_SORT_PROPERTIES =
            Set.of("id", "productCode", "productName", "description", "imageUrl", "price");

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PagedResult<ProductResponse>> getAllProducts(
            @RequestParam(defaultValue = "0", required = false) int pageNo,
            @RequestParam(defaultValue = "10", required = false) int pageSize,
            @RequestParam(defaultValue = "id", required = false) String sortBy,
            @RequestParam(defaultValue = "asc", required = false) String sortDir) {
        validatePagination(pageNo, pageSize, sortBy);
        PagedResult<ProductResponse> result =
                productService.findAllProducts(pageNo, pageSize, sortBy, sortDir);
        return ResponseEntity.ok(result);
    }

    @GetMapping(value = "/id/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.findProductByIdInternal(id));
    }

    @GetMapping(value = "/product-code/{productCode}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductResponse> getProductByProductCode(
            @PathVariable String productCode,
            @RequestParam(required = false, defaultValue = "false") Boolean fetchInStock) {
        return ResponseEntity.ok(
                productService.findProductByProductCode(productCode, fetchInStock));
    }

    @GetMapping(value = "/exists", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductExistsResponse> productExistsByProductCodes(
            @RequestParam List<String> productCodes) {
        boolean exists = productService.productExistsByProductCodes(productCodes);
        return ResponseEntity.ok(new ProductExistsResponse(exists));
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductResponse> createProduct(
            @RequestBody @Valid ProductRequest productRequest) {
        ProductResponse response = productService.saveProduct(productRequest);
        URI location = URI.create("/api/v1/catalog/id/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping(
            value = "/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id, @RequestBody @Valid ProductRequest productRequest) {
        return ResponseEntity.ok(productService.updateProduct(id, productRequest));
    }

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductResponse> deleteProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productService.deleteProductById(id));
    }

    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PagedResult<ProductResponse>> searchProducts(
            @RequestParam(required = false) String term,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0", required = false) int pageNo,
            @RequestParam(defaultValue = "10", required = false) int pageSize,
            @RequestParam(defaultValue = "id", required = false) String sortBy,
            @RequestParam(defaultValue = "asc", required = false) String sortDir) {
        validatePagination(pageNo, pageSize, sortBy);
        PagedResult<ProductResponse> result =
                productService.searchProducts(
                        term, minPrice, maxPrice, pageNo, pageSize, sortBy, sortDir);
        return ResponseEntity.ok(result);
    }

    private void validatePagination(int pageNo, int pageSize, String sortBy) {
        if (pageNo < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "pageNo must be non-negative");
        }
        if (pageSize <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pageSize must be positive");
        }
        if (!SUPPORTED_SORT_PROPERTIES.contains(sortBy)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unsupported sortBy property");
        }
    }
}
