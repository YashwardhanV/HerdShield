package dev.herdshield.api;

import dev.herdshield.cache.CacheStrategyRegistry;
import dev.herdshield.metrics.MetricsCollector;
import dev.herdshield.metrics.MetricsSnapshot;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class LabController {

    private final MetricsCollector metrics;
    private final CacheStrategyRegistry strategies;

    public LabController(MetricsCollector metrics, CacheStrategyRegistry strategies) {
        this.metrics = metrics;
        this.strategies = strategies;
    }

    @GetMapping("/metrics")
    public MetricsSnapshot metrics() {
        return metrics.snapshot();
    }

    @PostMapping("/metrics/reset")
    public MetricsSnapshot resetMetrics() {
        return metrics.reset();
    }

    @GetMapping("/cache")
    public List<CacheStrategyRegistry.StrategyInspection> inspectCache() {
        return strategies.inspectAll();
    }

    @DeleteMapping("/cache")
    public ResponseEntity<Void> clearCache() {
        strategies.clearAll();
        return ResponseEntity.noContent().build();
    }
}
