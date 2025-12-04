package com.example.portfolio.inventory.infra;

import com.example.portfolio.inventory.application.ProductListView;
import com.example.portfolio.inventory.application.ProductView;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 캐시에서 상품과 목록을 조회/저장하고 무효화한다.
 */
@Component
public class ProductCacheService {

    private static final String PRODUCT_KEY_PREFIX = "product:";
    private static final String PRODUCT_LIST_KEY_PREFIX = "product:list:";
    private static final String PRODUCT_LIST_KEYS_SET = "product:list:keys";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final RedisTemplate<String, Object> redisTemplate;

    public ProductCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<ProductView> findProduct(Long productId) {
        Object cached = redisTemplate.opsForValue().get(productKey(productId));
        return Optional.ofNullable(cached).map(ProductView.class::cast);
    }

    public void saveProduct(ProductView view) {
        redisTemplate.opsForValue().set(productKey(view.id()), view, TTL);
    }

    public void evictProduct(Long productId) {
        redisTemplate.delete(productKey(productId));
    }

    public Optional<ProductListView> findProductList(int page, int size) {
        Object cached = redisTemplate.opsForValue().get(listKey(page, size));
        return Optional.ofNullable(cached).map(ProductListView.class::cast);
    }

    public void saveProductList(ProductListView view) {
        String key = listKey(view.page(), view.size());
        redisTemplate.opsForValue().set(key, view, TTL);
        redisTemplate.opsForSet().add(PRODUCT_LIST_KEYS_SET, key);
    }

    public void evictProductListCaches() {
        Set<Object> keys = redisTemplate.opsForSet().members(PRODUCT_LIST_KEYS_SET);
        if (keys != null && !keys.isEmpty()) {
            Set<String> stringKeys = keys.stream()
                    .map(Object::toString)
                    .collect(java.util.stream.Collectors.toSet());
            redisTemplate.delete(stringKeys);
        }
        redisTemplate.delete(PRODUCT_LIST_KEYS_SET);
    }

    private String productKey(Long productId) {
        return PRODUCT_KEY_PREFIX + productId;
    }

    private String listKey(int page, int size) {
        return PRODUCT_LIST_KEY_PREFIX + page + ":" + size;
    }
}
