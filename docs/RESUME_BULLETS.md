# Resume Bullets

## Version A — no benchmark metrics

- Built a Java 21/Spring Boot cache-behavior laboratory with PostgreSQL, Caffeine, React, Docker Compose, application metrics, and repeatable high-concurrency experiment APIs.
- Implemented per-key request coalescing using `ConcurrentHashMap` and `CompletableFuture`, including shared failure propagation, bounded timeouts, exact future cleanup, and generation-guarded invalidation.
- Developed and deterministically tested naive cache-aside, stale-while-revalidate, TTL jitter, and negative caching to demonstrate stampede, avalanche, penetration, and freshness-versus-availability tradeoffs.

## Version B — replace placeholders only with actual benchmark results

- Reduced origin queries from `[ACTUAL_NAIVE]` to `[ACTUAL_SINGLE_FLIGHT]` under `[ACTUAL_CONCURRENCY]` concurrent same-key requests using per-key single-flight request coalescing.
- Served `[ACTUAL_STALE_RESPONSES]` stale responses while limiting refresh work to `[ACTUAL_BACKGROUND_REFRESHES]` background load(s) in a measured stale-while-revalidate experiment.
- Distributed `[ACTUAL_KEY_COUNT]` expirations across `[ACTUAL_BUCKET_COUNT]` measured time buckets and changed peak origin load from `[ACTUAL_FIXED_PEAK]` to `[ACTUAL_JITTER_PEAK]` using deterministic TTL jitter.

Do not use Version B until every placeholder is replaced from raw files produced by `scripts/run-benchmarks.sh` and recorded in `BENCHMARK_RESULTS.md`.
