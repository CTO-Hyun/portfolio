package com.example.portfolio.inventory.api;

import com.example.portfolio.inventory.api.dto.AdminProductCreateRequest;
import com.example.portfolio.inventory.api.dto.ProductListResponse;
import com.example.portfolio.inventory.api.dto.ProductResponse;
import com.example.portfolio.inventory.api.dto.StockAdjustRequest;
import com.example.portfolio.inventory.application.AdjustStockCommand;
import com.example.portfolio.inventory.application.CreateProductCommand;
import com.example.portfolio.inventory.application.ProductApplicationService;
import com.example.portfolio.inventory.application.ProductListView;
import com.example.portfolio.inventory.application.ProductView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상품 조회 및 관리자 API를 노출한다.
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class ProductController {

    private final ProductApplicationService productApplicationService;

    public ProductController(ProductApplicationService productApplicationService) {
        this.productApplicationService = productApplicationService;
    }

    @GetMapping("/products")
    public ProductListResponse listProducts(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지는 0 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "사이즈는 1 이상이어야 합니다.") @Max(value = 100, message = "사이즈는 100 이하여야 합니다.") int size) {
        ProductListView view = productApplicationService.listProducts(page, size);
        return toResponse(view);
    }

    @GetMapping("/products/{productId}")
    public ProductResponse getProduct(@PathVariable Long productId) {
        ProductView view = productApplicationService.getProduct(productId);
        return toResponse(view);
    }

    @PostMapping("/admin/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse createProduct(@Valid @RequestBody AdminProductCreateRequest request) {
        ProductView view = productApplicationService.createProduct(new CreateProductCommand(
                request.sku(),
                request.name(),
                request.description(),
                request.price(),
                request.initialQuantity()));
        return toResponse(view);
    }

    @PostMapping("/admin/products/{productId}/stock-adjust")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse adjustStock(@PathVariable Long productId, @Valid @RequestBody StockAdjustRequest request) {
        ProductView view = productApplicationService.adjustStock(new AdjustStockCommand(productId, request.quantityDelta()));
        return toResponse(view);
    }

    private ProductResponse toResponse(ProductView view) {
        return new ProductResponse(view.id(), view.sku(), view.name(), view.description(), view.price(), view.quantity());
    }

    private ProductListResponse toResponse(ProductListView view) {
        return new ProductListResponse(
                view.items().stream().map(this::toResponse).toList(),
                view.totalElements(),
                view.totalPages(),
                view.page(),
                view.size());
    }
}
