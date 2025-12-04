package com.example.portfolio.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.portfolio.common.exception.BusinessException;
import com.example.portfolio.inventory.domain.Product;
import com.example.portfolio.inventory.domain.Stock;
import com.example.portfolio.inventory.infra.ProductCacheService;
import com.example.portfolio.inventory.infra.ProductRepository;
import com.example.portfolio.inventory.infra.StockRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductApplicationServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private ProductCacheService productCacheService;

    private ProductApplicationService productApplicationService;

    @BeforeEach
    void setUp() {
        productApplicationService = new ProductApplicationService(productRepository, stockRepository, productCacheService);
    }

    @Test
    void createProduct_succeeds_whenSkuUnique() {
        when(productRepository.findBySku("SKU-1")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            ReflectionTestUtils.setField(product, "id", 1L);
            return product;
        });
        when(stockRepository.save(any(Stock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductView view = productApplicationService.createProduct(
                new CreateProductCommand("SKU-1", "상품", "설명", BigDecimal.TEN, 5));

        assertThat(view.id()).isEqualTo(1L);
        assertThat(view.quantity()).isEqualTo(5);
        verify(productCacheService).saveProduct(any());
        verify(productCacheService).evictProductListCaches();
    }

    @Test
    void createProduct_fails_whenSkuDuplicate() {
        Product existing = Product.create("SKU-1", "상품", "설명", BigDecimal.ONE);
        when(productRepository.findBySku("SKU-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> productApplicationService.createProduct(
                new CreateProductCommand("SKU-1", "상품", "설명", BigDecimal.TEN, 0)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void adjustStock_fails_whenResultNegative() {
        Product product = Product.create("SKU-1", "상품", "설명", BigDecimal.ONE);
        ReflectionTestUtils.setField(product, "id", 1L);
        Stock stock = Stock.initialize(product, 3);
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));

        assertThatThrownBy(() -> productApplicationService.adjustStock(new AdjustStockCommand(1L, -5)))
                .isInstanceOf(BusinessException.class);
    }
}
