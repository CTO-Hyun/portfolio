package com.example.portfolio.inventory.application;

import com.example.portfolio.common.exception.BusinessException;
import com.example.portfolio.common.exception.ErrorCode;
import com.example.portfolio.inventory.domain.Product;
import com.example.portfolio.inventory.domain.Stock;
import com.example.portfolio.inventory.infra.ProductCacheService;
import com.example.portfolio.inventory.infra.ProductRepository;
import com.example.portfolio.inventory.infra.StockRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품/재고 읽기와 관리자 쓰기 시나리오를 담당한다.
 */
@Service
@Transactional(readOnly = true)
public class ProductApplicationService {

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final ProductCacheService productCacheService;

    public ProductApplicationService(
            ProductRepository productRepository,
            StockRepository stockRepository,
            ProductCacheService productCacheService) {
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.productCacheService = productCacheService;
    }

    /**
     * SKU 중복을 확인하고 상품/재고를 함께 생성한다.
     */
    @Transactional
    public ProductView createProduct(CreateProductCommand command) {
        productRepository.findBySku(command.sku())
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.CONFLICT_ERROR, "이미 존재하는 SKU입니다.");
                });
        Product product = Product.create(command.sku(), command.name(), command.description(), command.price());
        Product savedProduct = productRepository.saveAndFlush(product);
        Stock stock = Stock.initialize(savedProduct, command.initialQuantity());
        Stock savedStock = stockRepository.save(stock);
        ProductView view = ProductView.from(savedProduct, savedStock);
        productCacheService.saveProduct(view);
        productCacheService.evictProductListCaches();
        return view;
    }

    /**
     * 단일 상품 조회 시 캐시를 우선 조회한다.
     */
    public ProductView getProduct(Long productId) {
        return productCacheService.findProduct(productId)
                .orElseGet(() -> {
                    Product product = productRepository.findById(productId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "상품을 찾을 수 없습니다."));
                    Stock stock = stockRepository.findById(productId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "재고를 찾을 수 없습니다."));
                    ProductView view = ProductView.from(product, stock);
                    productCacheService.saveProduct(view);
                    return view;
                });
    }

    /**
     * 페이지 단위 상품 목록을 조회하며 목록 캐시를 활용한다.
     */
    public ProductListView listProducts(int page, int size) {
        return productCacheService.findProductList(page, size)
                .orElseGet(() -> {
                    Pageable pageable = PageRequest.of(page, size, Sort.by("id").ascending());
                    Page<Product> result = productRepository.findAll(pageable);
                    List<ProductView> items = toViews(result.getContent());
                    ProductListView view = new ProductListView(items, result.getTotalElements(), result.getTotalPages(), page, size);
                    productCacheService.saveProductList(view);
                    return view;
                });
    }

    /**
     * 재고 증감을 적용하고 음수를 허용하지 않는다.
     */
    @Transactional
    public ProductView adjustStock(AdjustStockCommand command) {
        Stock stock = stockRepository.findById(command.productId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "재고를 찾을 수 없습니다."));
        stock.applyDelta(command.quantityDelta());
        Product product = stock.getProduct();
        ProductView view = ProductView.from(product, stock);
        productCacheService.saveProduct(view);
        productCacheService.evictProductListCaches();
        return view;
    }

    private List<ProductView> toViews(List<Product> products) {
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> ids = products.stream().map(Product::getId).toList();
        Map<Long, Stock> stockMap = stockRepository.findByProductIdIn(ids).stream()
                .collect(Collectors.toMap(Stock::getProductId, Function.identity()));
        return products.stream()
                .map(product -> ProductView.from(product, stockMap.get(product.getId())))
                .toList();
    }
}
