/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.retail.catalog.internal.application.ProductService;
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
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;

    @Mock private ApplicationEventPublisher eventPublisher;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, eventPublisher);
    }

    @Test
    @DisplayName("findAllProducts returns paged products")
    void findAllProducts_ReturnsPagedResult() {
        Product p1 =
                new Product(
                        1L, "P001", "Product 1", "Desc 1", BigDecimal.valueOf(10.0), "img1.jpg");
        given(productRepository.findAll(any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(p1)));

        PagedResult<ProductResponse> result = productService.findAllProducts(0, 10, "id", "asc");

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().getFirst().productCode()).isEqualTo("P001");
        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.pageNumber()).isZero();
        assertThat(result.data().getFirst().inStock()).isNull();
    }

    @Test
    @DisplayName("findProductByIdInternal returns product when found")
    void findProductByIdInternal_Found() {
        Product p =
                new Product(
                        1L, "P001", "Product 1", "Desc 1", BigDecimal.valueOf(10.0), "img1.jpg");
        given(productRepository.findById(1L)).willReturn(Optional.of(p));

        ProductResponse response = productService.findProductByIdInternal(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.productCode()).isEqualTo("P001");
        assertThat(response.inStock()).isNull();
    }

    @Test
    @DisplayName("findProductByIdInternal throws ProductNotFoundException when not found")
    void findProductByIdInternal_NotFound_ThrowsException() {
        given(productRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findProductByIdInternal(999L))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("findProductByProductCode returns product when found")
    void findProductByProductCode_Found() {
        Product p =
                new Product(
                        1L, "P001", "Product 1", "Desc 1", BigDecimal.valueOf(10.0), "img1.jpg");
        given(productRepository.findByProductCode("P001")).willReturn(Optional.of(p));

        ProductResponse response = productService.findProductByProductCode("P001", false);

        assertThat(response.productCode()).isEqualTo("P001");
        assertThat(response.inStock()).isNull();
    }

    @Test
    @DisplayName("findProductByProductCode throws ProductNotFoundException when not found")
    void findProductByProductCode_NotFound_ThrowsException() {
        given(productRepository.findByProductCode("UNKNOWN")).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findProductByProductCode("UNKNOWN", false))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("saveProduct saves product and publishes ProductCreatedEvent")
    void saveProduct_Success_PublishesEvent() {
        ProductRequest request =
                new ProductRequest(
                        "P100", "New Product", "Description", "img.jpg", BigDecimal.valueOf(99.99));
        given(productRepository.existsByProductCode("P100")).willReturn(false);

        Product savedEntity =
                new Product(
                        10L,
                        "P100",
                        "New Product",
                        "Description",
                        BigDecimal.valueOf(99.99),
                        "img.jpg");
        given(productRepository.saveAndFlush(any(Product.class))).willReturn(savedEntity);

        ProductResponse response = productService.saveProduct(request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.productCode()).isEqualTo("P100");
        assertThat(response.inStock()).isNull();

        ArgumentCaptor<ProductCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(ProductCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        ProductCreatedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.productCode()).isEqualTo("P100");
        assertThat(publishedEvent.productName()).isEqualTo("New Product");
        assertThat(publishedEvent.price()).isEqualTo(BigDecimal.valueOf(99.99));
    }

    @Test
    @DisplayName("saveProduct throws ProductAlreadyExistsException when code already exists")
    void saveProduct_DuplicateCode_ThrowsException() {
        ProductRequest request =
                new ProductRequest(
                        "P001", "Existing Product", "Desc", "img.jpg", BigDecimal.valueOf(10.0));
        given(productRepository.existsByProductCode("P001")).willReturn(true);

        assertThatThrownBy(() -> productService.saveProduct(request))
                .isInstanceOf(ProductAlreadyExistsException.class);

        verify(productRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("updateProduct updates and returns product")
    void updateProduct_Success() {
        Product existing =
                new Product(
                        1L, "P001", "Old Name", "Old Desc", BigDecimal.valueOf(10.0), "old.jpg");
        given(productRepository.findById(1L)).willReturn(Optional.of(existing));

        Product updatedEntity =
                new Product(
                        1L,
                        "P001",
                        "Updated Name",
                        "Updated Desc",
                        BigDecimal.valueOf(15.0),
                        "new.jpg");
        given(productRepository.saveAndFlush(existing)).willReturn(updatedEntity);

        ProductRequest request =
                new ProductRequest(
                        "P001",
                        "Updated Name",
                        "Updated Desc",
                        "new.jpg",
                        BigDecimal.valueOf(15.0));
        ProductResponse response = productService.updateProduct(1L, request);

        assertThat(response.inStock()).isNull();
        assertThat(response.productName()).isEqualTo("Updated Name");
        assertThat(response.price()).isEqualTo(BigDecimal.valueOf(15.0));
    }

    @Test
    @DisplayName("deleteProductById deletes product and returns response")
    void deleteProductById_Success() {
        Product existing =
                new Product(1L, "P001", "Product 1", "Desc", BigDecimal.valueOf(10.0), "img.jpg");
        given(productRepository.findById(1L)).willReturn(Optional.of(existing));

        ProductResponse response = productService.deleteProductById(1L);

        assertThat(response.id()).isEqualTo(1L);
        verify(productRepository).delete(existing);
        assertThat(response.inStock()).isNull();
    }

    @Test
    @DisplayName("public facade findProductByCode returns ProductDto")
    void facade_findProductByCode() {
        Product p =
                new Product(1L, "P001", "Product 1", "Desc", BigDecimal.valueOf(10.0), "img.jpg");
        given(productRepository.findByProductCode("P001")).willReturn(Optional.of(p));

        Optional<ProductDto> dto = productService.findProductByCode("P001");

        assertThat(dto).isPresent();
        assertThat(dto.get().productCode()).isEqualTo("P001");
    }

    @Test
    @DisplayName("public facade existsByProductCodes returns true when all codes exist")
    void facade_existsByProductCodes() {
        given(productRepository.countByProductCodeIn(any())).willReturn(2L);

        boolean exists = productService.existsByProductCodes(List.of("P001", "P002"));

        assertThat(exists).isTrue();
    }

    @Test
    void existsByProductCodes_IgnoresDuplicates() {
        given(productRepository.countByProductCodeIn(any())).willReturn(2L);
        assertThat(productService.existsByProductCodes(List.of("P001", "P001", "P002"))).isTrue();
        verify(productRepository).countByProductCodeIn(Set.of("P001", "P002"));
    }

    @Test
    void existsByProductCodes_DuplicatesDoNotHideMissingCodes() {
        given(productRepository.countByProductCodeIn(any())).willReturn(1L);
        assertThat(productService.existsByProductCodes(List.of("P001", "P001", "MISSING")))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void productCodeLookup_DoesNotTreatInventoryFlagAsStock(boolean fetchInStock) {
        given(productRepository.findByProductCode("P001"))
                .willReturn(Optional.of(new Product(1L, "P001", "Name", null, null, null)));
        assertThat(productService.findProductByProductCode("P001", fetchInStock).inStock())
                .isNull();
    }

    @Test
    void searchProducts_PreservesZeroBasedPageAndUnknownStock() {
        var page = PageRequest.of(2, 1);
        given(productRepository.findAll(any(Pageable.class)))
                .willReturn(
                        new PageImpl<>(
                                List.of(new Product(1L, "P001", "Name", null, null, null)),
                                page,
                                4));
        var result = productService.searchProducts(null, null, null, 2, 1, "id", "asc");
        assertThat(result.pageNumber()).isEqualTo(2);
        assertThat(result.hasPrevious()).isTrue();
        assertThat(result.hasNext()).isTrue();
        assertThat(result.data().getFirst().inStock()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void productCodeConstraint_IsTranslatedForCreateAndUpdate(boolean update) {
        var violation = integrityViolation("uc_product_code");
        given(productRepository.saveAndFlush(any(Product.class))).willThrow(violation);
        var request = new ProductRequest("DUPLICATE", "Name", null, null, null);
        if (update) {
            given(productRepository.findById(1L))
                    .willReturn(Optional.of(new Product(1L, "ORIGINAL", "Name", null, null, null)));
        }
        assertThatThrownBy(
                        () -> {
                            if (update) productService.updateProduct(1L, request);
                            else productService.saveProduct(request);
                        })
                .isInstanceOf(ProductAlreadyExistsException.class)
                .hasMessageContaining("DUPLICATE");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"products_pkey", "uc_product_code_other"})
    void otherIntegrityViolations_AreRethrown(String constraint) {
        var violation = integrityViolation(constraint);
        given(productRepository.saveAndFlush(any(Product.class))).willThrow(violation);
        given(productRepository.findById(1L))
                .willReturn(Optional.of(new Product(1L, "P001", "Name", null, null, null)));
        var request = new ProductRequest("P001", "Name", null, null, null);
        assertThatThrownBy(() -> productService.saveProduct(request)).isSameAs(violation);
        assertThatThrownBy(() -> productService.updateProduct(1L, request)).isSameAs(violation);
    }

    @Test
    void nonHibernateIntegrityViolation_IsRethrown() {
        var violation = new DataIntegrityViolationException("Other integrity failure");
        given(productRepository.saveAndFlush(any(Product.class))).willThrow(violation);
        given(productRepository.findById(1L))
                .willReturn(Optional.of(new Product(1L, "P001", "Name", null, null, null)));
        var request = new ProductRequest("P001", "Name", null, null, null);
        assertThatThrownBy(() -> productService.saveProduct(request)).isSameAs(violation);
        assertThatThrownBy(() -> productService.updateProduct(1L, request)).isSameAs(violation);
    }

    private DataIntegrityViolationException integrityViolation(String constraint) {
        return new DataIntegrityViolationException(
                "Persistence failed",
                new RuntimeException(
                        new ConstraintViolationException(
                                "Constraint failed", new SQLException(), constraint)));
    }
}
