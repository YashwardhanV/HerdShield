# HerdShield — Project Plan

HerdShield is a cache-behavior laboratory built to make concurrency failures visible. The product domain is deliberately small: products are read from PostgreSQL and may be cached in-process. The engineering work is in controlling what happens when many requests arrive at the same cache boundary.

## 1. What a cache is

A cache keeps a reusable copy of data closer to the caller than the authoritative origin. HerdShield uses an in-process Caffeine cache; PostgreSQL remains the source of truth. A cache trades memory and possible staleness for fewer origin queries and lower read latency.

## 2. Cache hit

A cache hit means a usable entry already exists for the requested key. No product query is needed. HerdShield records both ordinary positive hits and negative-cache hits so the distinction is visible.

## 3. Cache miss

A cache miss means no usable entry exists. The application must load from the origin, decide whether the result is cacheable, store it when appropriate, and return it. Concurrent misses are the dangerous part: “miss” is an observation, not mutual exclusion.

## 4. TTL

Time to live (TTL) is the duration for which an entry remains usable. HerdShield records monotonic deadlines rather than repeatedly subtracting wall-clock timestamps. Single-flight and naive entries have one expiry deadline. Stale-while-revalidate entries have separate `freshUntil` and `staleUntil` deadlines.

## 5. Eviction

Eviction removes entries to bound memory or discard unusable data. Caffeine provides size-based eviction while HerdShield owns the teaching-oriented per-entry expiry rules. Expiration is time-based invalidation; eviction can also occur because the cache reaches its maximum size.

## 6. Cache stampede / thundering herd

A stampede happens when many callers observe the same hot key as missing or expired and all load it from the origin. The application may have enough capacity to handle the client requests but not the multiplied database work. HerdShield reproduces this with a start gate that releases 100+ workers against one expired key.

## 7. Cache penetration

Cache penetration is repeated traffic for keys that do not exist. A normal cache has nothing positive to store, so every request can reach the database. HerdShield’s negative-cache strategy stores an explicit `NOT_FOUND` result for a short, separate TTL. Origin exceptions are never converted into missing values.

## 8. Cache avalanche

An avalanche is a burst caused by many keys becoming unusable together, often after a bulk warm-up or restart. Fixed TTLs preserve that alignment. HerdShield compares fixed TTL deadlines with deterministic per-key jitter and reports the expiration histogram and peak bucket.

## 9. Why naive caching fails under concurrency

This sequence is not atomic:

1. read cache;
2. observe miss;
3. query origin;
4. write cache.

Two or 200 threads can all finish step 2 before any thread reaches step 4. A thread-safe cache protects its own data structure; it does not automatically make a multi-step cache-aside workflow single-flight. The naive strategy intentionally preserves this race as the baseline.

## 10. Proposed architecture

- React + TypeScript dashboard calls one Spring MVC backend.
- Spring Boot is a modular monolith with API, experiment, cache, metrics, and product/origin packages.
- PostgreSQL is the source of truth and is seeded at startup.
- Caffeine bounds each in-process strategy cache. Strategy-owned metadata models expiry and staleness.
- One bounded executor runs coalesced loads and background refreshes. No global key lock is used.
- Application metrics and experiment-local timings provide p50/p95/p99 without requiring an observability stack.
- Docker Compose runs PostgreSQL, backend, and the static frontend/reverse proxy.

## 11. Algorithms being implemented

### Naive cache-aside

Check, load, then put. It is correct for ordinary cache-aside semantics but deliberately offers no miss coalescing.

### Per-key single-flight

An in-flight `ConcurrentHashMap<K, CompletableFuture<LoadResult<V>>>` elects one loader using `putIfAbsent`. Followers join that future. Completion, failure, and timeout remove the exact `(key, future)` pair. A per-key generation prevents an older load from repopulating an entry after invalidation.

### Stale-while-revalidate

- Before `freshUntil`: return a fresh hit.
- Between `freshUntil` and `staleUntil`: return stale immediately and elect one background refresher.
- After `staleUntil`: block behind one refresh/load.

A failed background refresh increments `refreshFailures` and leaves the existing stale value available only until its original `staleUntil`.

### TTL jitter

For key `k`, seed `s`, base TTL `b`, and jitter bound `j`:

`effectiveTtl = max(1ms, b + (floorMod(mix64(hash(k) xor s), 2j + 1) - j))`

This gives a deterministic uniform offset in `[-j, +j]`. Determinism makes experiment reruns comparable. Jitter distributes different keys; it does not protect a single hot key.

### Negative caching

The cache stores a typed `LoadResult`, so an absent entry and a cached missing result are different states. Positive values use the normal TTL; missing values use a short negative TTL. Exceptions bypass the cache.

### Write invalidation

After a successful `saveAndFlush`, the product service invalidates that key in every strategy. In-flight generations are advanced so an older read cannot overwrite the post-write cache state. A read already in progress may still return its earlier snapshot; the project documents this boundary instead of claiming linearizability.

## 12. Benchmark strategy

The experiment API accepts a strategy, key, request count, concurrency, TTL, optional controlled origin latency, and repeatable seed. A start gate aligns workers. Each run reports real measured latency samples, errors, and metric deltas. The primary comparison uses the same environment and same-key workload for naive versus single-flight.

Five supplied benchmark scenarios cover:

- naive same-key stampede;
- single-flight same-key coalescing;
- stale response plus one refresh;
- repeated missing-key penetration;
- fixed versus jittered multi-key expiration distribution.

`docs/BENCHMARK_RESULTS.md` contains no prefilled performance claims. The script writes raw JSON so results can be copied without transcription guesswork.

## 13. Scope intentionally excluded

- Redis and distributed locking: one process is enough to teach the target race; Redis would change the failure model and setup cost.
- Authentication: experiment controls are local demonstration tools, and auth does not strengthen the caching lesson.
- Microservices, Kafka, queues, service discovery, Kubernetes, API gateways, CQRS, and event sourcing.
- Prometheus/Grafana and distributed tracing stacks.
- A production benchmarking framework such as JMH for HTTP end-to-end tests; HerdShield measures user-visible request workflows instead.
- Claims of global cache coherence or linearizable reads during concurrent writes.
