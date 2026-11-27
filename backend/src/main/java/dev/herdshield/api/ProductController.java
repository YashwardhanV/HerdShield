package dev.herdshield.api;

import dev.herdshield.cache.CachePolicy;
import dev.herdshield.cache.CachePolicyFactory;
import dev.herdshield.cache.CacheStrategyType;
import dev.herdshield.origin.OriginMode;
import dev.herdshield.origin.OriginOptions;
import dev.herdshield.product.ProductResponse;
import dev.herdshield.product.ProductService;
import dev.herdshield.product.UpdateProductRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService products;
    private final CachePolicyFactory policies;

    public ProductController(ProductService products, CachePolicyFactory policies) {
        this.products = products;
        this.policies = policies;
    }

    @GetMapping("/{id}")
    public ProductResponse get(
            @PathVariable @Positive long id,
            @RequestParam(defaultValue = "SINGLE_FLIGHT") CacheStrategyType strategy,
            @RequestParam(required = false) @Min(1) @Max(60_000) Long ttlMs,
            @RequestParam(required = false) @Min(0) @Max(60_000) Long staleTtlMs,
            @RequestParam(required = false) @Min(1) @Max(60_000) Long negativeTtlMs,
            @RequestParam(required = false) @Min(1) @Max(30_000) Long loadTimeoutMs,
            @RequestParam(defaultValue = "0") @Min(0) @Max(60_000) Long jitterMs,
            @RequestParam(defaultValue = "42") long seed,
            @RequestParam(defaultValue = "REAL_DATABASE") OriginMode originMode,
            @RequestParam(defaultValue = "0") @Min(0) @Max(5_000) long originLatencyMs) {
        CachePolicy policy = policies.create(ttlMs, staleTtlMs, negativeTtlMs, loadTimeoutMs, jitterMs, seed);
        return products.getProduct(id, strategy, policy, new OriginOptions(originMode, originLatencyMs));
    }

    @PutMapping("/{id}")
    public ProductResponse update(
            @PathVariable @Positive long id, @Valid @RequestBody UpdateProductRequest request) {
        return products.updateProduct(id, request);
    }
}
