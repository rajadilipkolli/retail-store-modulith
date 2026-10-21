/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/
package com.example.retail.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.retail.catalog.internal.application.ProductService;
import com.example.retail.catalog.internal.domain.ProductAlreadyExistsException;
import com.example.retail.catalog.internal.domain.ProductNotFoundException;
import com.example.retail.catalog.internal.web.CatalogExceptionHandler;
import com.example.retail.catalog.internal.web.ProductController;
import com.example.retail.catalog.internal.web.models.PagedResult;
import com.example.retail.catalog.internal.web.models.ProductRequest;
import com.example.retail.catalog.internal.web.models.ProductResponse;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(controllers = ProductController.class)
@Import(CatalogExceptionHandler.class)
class ProductControllerTest {

    @Autowired private MockMvcTester mockMvcTester;

    @MockitoBean private ProductService productService;

    @Test
    @DisplayName("GET /api/v1/catalog returns paged products")
    void getAllProducts_v1() {
        ProductResponse p1 =
                new ProductResponse(
                        1L,
                        "P001",
                        "Product 1",
                        "Desc",
                        "img.jpg",
                        BigDecimal.valueOf(99.99),
                        null);
        PagedResult<ProductResponse> pagedResult = new PagedResult<>(new PageImpl<>(List.of(p1)));

        given(productService.findAllProducts(0, 10, "id", "asc")).willReturn(pagedResult);

        assertThat(mockMvcTester.get().uri("/api/v1/catalog"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data")
                .asArray()
                .hasSize(1);
    }

    @Test
    @DisplayName("GET /api/v1/catalog/id/{id} returns product when found")
    void getProductById_Found() {
        ProductResponse response =
                new ProductResponse(
                        1L,
                        "P001",
                        "Product 1",
                        "Desc",
                        "img.jpg",
                        BigDecimal.valueOf(99.99),
                        null);
        given(productService.findProductByIdInternal(1L)).willReturn(response);

        assertThat(mockMvcTester.get().uri("/api/v1/catalog/id/1"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.productCode")
                .isEqualTo("P001");
    }

    @Test
    @DisplayName("GET /api/v1/catalog/id/{id} returns 404 ProblemDetail when not found")
    void getProductById_NotFound() {
        given(productService.findProductByIdInternal(999L))
                .willThrow(new ProductNotFoundException(999L));

        assertThat(mockMvcTester.get().uri("/api/v1/catalog/id/999"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Product Not Found");
    }

    @Test
    @DisplayName("GET /api/v1/catalog/product-code/{code} returns product")
    void getProductByCode() {
        ProductResponse response =
                new ProductResponse(
                        1L,
                        "P001",
                        "Product 1",
                        "Desc",
                        "img.jpg",
                        BigDecimal.valueOf(99.99),
                        null);
        given(productService.findProductByProductCode("P001", false)).willReturn(response);

        assertThat(mockMvcTester.get().uri("/api/v1/catalog/product-code/P001"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.productCode")
                .isEqualTo("P001");
    }

    @Test
    @DisplayName("GET /api/v1/catalog/exists returns boolean response")
    void productExists() {
        given(productService.productExistsByProductCodes(List.of("P001", "P002"))).willReturn(true);

        assertThat(
                        mockMvcTester
                                .get()
                                .uri("/api/v1/catalog/exists")
                                .param("productCodes", "P001", "P002"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.exists")
                .isEqualTo(true);
    }

    @Test
    @DisplayName("POST /api/v1/catalog with valid payload returns 201 Created and Location header")
    void createProduct_Valid() {
        ProductResponse response =
                new ProductResponse(
                        10L,
                        "P101",
                        "New Prod",
                        "Desc",
                        "img.jpg",
                        BigDecimal.valueOf(49.99),
                        null);
        given(productService.saveProduct(any(ProductRequest.class))).willReturn(response);

        String json =
                """
                {
                    "productCode": "P101",
                    "productName": "New Prod",
                    "description": "Desc",
                    "imageUrl": "img.jpg",
                    "price": 49.99
                }
                """;

        assertThat(
                        mockMvcTester
                                .post()
                                .uri("/api/v1/catalog")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "/api/v1/catalog/id/10")
                .bodyJson()
                .extractingPath("$.productCode")
                .isEqualTo("P101");
    }

    @Test
    @DisplayName(
            "POST /api/v1/catalog with invalid payload returns 400 Bad Request with violations")
    void createProduct_Invalid_Returns400() {
        String json =
                """
                {
                    "productCode": "",
                    "productName": "New Prod",
                    "price": -10.0
                }
                """;

        assertThat(
                        mockMvcTester
                                .post()
                                .uri("/api/v1/catalog")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Constraint Violation");
    }

    @Test
    @DisplayName("POST /api/v1/catalog duplicate code returns 409 Conflict")
    void createProduct_Duplicate_Returns409() {
        given(productService.saveProduct(any(ProductRequest.class)))
                .willThrow(new ProductAlreadyExistsException("P001"));

        String json =
                """
                {
                    "productCode": "P001",
                    "productName": "Duplicate Prod",
                    "price": 49.99
                }
                """;

        assertThat(
                        mockMvcTester
                                .post()
                                .uri("/api/v1/catalog")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Product Already Exists");
    }

    @Test
    @DisplayName("PUT /api/v1/catalog/{id} updates product")
    void updateProduct() {
        ProductResponse response =
                new ProductResponse(
                        1L,
                        "P001",
                        "Updated Name",
                        "Desc",
                        "img.jpg",
                        BigDecimal.valueOf(59.99),
                        null);
        given(productService.updateProduct(eq(1L), any(ProductRequest.class))).willReturn(response);

        String json =
                """
                {
                    "productCode": "P001",
                    "productName": "Updated Name",
                    "description": "Desc",
                    "price": 59.99
                }
                """;

        assertThat(
                        mockMvcTester
                                .put()
                                .uri("/api/v1/catalog/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.productName")
                .isEqualTo("Updated Name");
    }

    @Test
    @DisplayName("DELETE /api/v1/catalog/{id} deletes product")
    void deleteProduct() {
        ProductResponse response =
                new ProductResponse(
                        1L,
                        "P001",
                        "Product 1",
                        "Desc",
                        "img.jpg",
                        BigDecimal.valueOf(99.99),
                        null);
        given(productService.deleteProductById(1L)).willReturn(response);

        assertThat(mockMvcTester.delete().uri("/api/v1/catalog/1"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.productCode")
                .isEqualTo("P001");
    }

    @ParameterizedTest
    @CsvSource({
        "/api/v1/catalog, pageNo, -1",
        "/api/v1/catalog, pageSize, 0",
        "/api/v1/catalog, pageSize, -1",
        "/api/v1/catalog, sortBy, unknown",
        "/api/v1/catalog/search, pageNo, -1",
        "/api/v1/catalog/search, pageSize, 0",
        "/api/v1/catalog/search, pageSize, -1",
        "/api/v1/catalog/search, sortBy, unknown"
    })
    void invalidPagination_Returns400BeforeCallingService(
            String uri, String parameter, String value) {
        assertThat(mockMvcTester.get().uri(uri).param(parameter, value))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(productService);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void largePageSizeAndSupportedSort_AreAccepted(boolean search) {
        var result = new PagedResult<ProductResponse>(new PageImpl<>(List.of()));
        if (search) {
            given(productService.searchProducts(null, null, null, 0, 100000, "price", "desc"))
                    .willReturn(result);
        } else {
            given(productService.findAllProducts(0, 100000, "price", "desc")).willReturn(result);
        }
        assertThat(
                        mockMvcTester
                                .get()
                                .uri(search ? "/api/v1/catalog/search" : "/api/v1/catalog")
                                .param("pageSize", "100000")
                                .param("sortBy", "price")
                                .param("sortDir", "desc"))
                .hasStatusOk();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nullProductName_Returns400ForCreateAndUpdate(boolean update) {
        var request =
                update
                        ? mockMvcTester.put().uri("/api/v1/catalog/1")
                        : mockMvcTester.post().uri("/api/v1/catalog");
        assertThat(
                        request.contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productCode\":\"P001\",\"productName\":null}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.violations[0].message")
                .isEqualTo("Product name can't be null");
        verifyNoInteractions(productService);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nullPrice_RemainsOptionalForCreateAndUpdate(boolean update) {
        var response = new ProductResponse(1L, "P001", "Name", null, null, null, null);
        if (update) {
            given(productService.updateProduct(eq(1L), any(ProductRequest.class)))
                    .willReturn(response);
        } else {
            given(productService.saveProduct(any(ProductRequest.class))).willReturn(response);
        }
        var request =
                update
                        ? mockMvcTester.put().uri("/api/v1/catalog/1")
                        : mockMvcTester.post().uri("/api/v1/catalog");
        assertThat(
                        request.contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productCode\":\"P001\",\"productName\":\"Name\"}"))
                .hasStatus(update ? HttpStatus.OK : HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.inStock")
                .isNull();
    }
}
