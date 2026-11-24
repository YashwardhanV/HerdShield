# HerdShield Interview Guide

Use this guide to explain decisions, not to memorize slogans. A strong answer starts with the observed failure, names the invariant, walks through the code path, and admits the boundary of the guarantee.

## 1. Cache-aside

**What problem exists?** Reading PostgreSQL for every request spends database connections, CPU, and latency on data that may be safely reused.

**Why does the naive implementation fail?** Cache-aside is usually described as “check, load, put.” Those are three separate operations. A thread-safe cache makes each operation safe but does not make their composition atomic. Concurrent callers can all observe the same miss.

**How does our implementation work?** `NaiveCacheAsideStrategy` checks a Caffeine entry and its monotonic deadline. A usable entry is a hit. Otherwise it records a miss, calls the supplied origin loader, and caches only a found value.

**Tradeoffs:** It is simple, keeps database writes independent, and works well for many non-hot workloads. It gives no same-key miss suppression and can briefly serve stale data unless writes invalidate it.

**Alternatives:** Read-through/loading cache, write-through, write-behind, refresh-ahead, or no cache when the origin is already cheap.

**Failure cases:** Hot-key expiry, cold-start bursts, origin failure, and an update racing with a read.

**Important classes:** `NaiveCacheAsideStrategy`, `TimedEntry`, `ProductOriginService`, `ProductService`.

**Likely questions:** Why is `ConcurrentHashMap` alone not enough? When would you intentionally choose naive cache-aside? Why not cache exceptions?

## 2. Cache stampede / thundering herd

**What problem exists?** A single popular entry expires and many requests simultaneously create duplicate origin work. The multiplication occurs inside the application: 100 client requests can become 100 database queries.

**Why does the naive implementation fail?** Every caller observes the same expired state before any caller has completed the replacement write. Faster caches can make the check faster without closing the race.

**How does our implementation demonstrate it?** `NaiveCacheAsideConcurrencyTest` lets 100 threads enter the loader and holds them on a latch until every caller has missed. `ExperimentService` uses a start gate to align a configurable number of workers. `originLoads` proves how much work crossed the cache boundary.

**Tradeoffs:** Artificially aligned workers model a worst-case boundary, while real traffic has a distribution. Controlled latency increases the overlap window but still performs a real repository query.

**Alternatives:** Single-flight, mutex per key, stale-while-revalidate, early refresh, origin rate limiting, or a loading cache with atomic per-key load semantics.

**Failure cases:** A key can re-stampede after a load timeout; multiple application instances can each elect their own loader; many different keys are an avalanche rather than one herd.

**Important classes:** `ExperimentService`, `NaiveCacheAsideConcurrencyTest`, `MetricsCollector`.

**Likely questions:** Is one origin load guaranteed or approximate? What changes with three backend replicas? How do you prove a stampede rather than assume it?

## 3. Single-flight / request coalescing

**What problem exists?** Overlapping requests need one shared result for a key without serializing unrelated keys.

**Why does the naive implementation fail?** A global `synchronized` block would suppress duplicates but also block different keys. A `containsKey` followed by `put` has another check-then-act race. Holding a map-computation lock while running JDBC risks blocking map progress and makes failure handling opaque.

**How does our implementation work?**

1. Check the completed-value cache.
2. Create an incomplete `CompletableFuture` candidate.
3. `inFlight.putIfAbsent(key, candidate)` elects one leader.
4. A follower joins the existing future and increments `coalescedRequests`.
5. The leader submits the origin load to a bounded executor.
6. Success populates the completed-value cache only if the invalidation generation is unchanged.
7. Success, exception, timeout, or executor rejection completes the future.
8. `whenComplete` calls `remove(key, candidate)`, so an older completion cannot remove a newer window.

**Tradeoffs:** Followers inherit leader latency and failure. Timeout bounds coordination-state lifetime but can permit a later load while an uninterruptible older JDBC call is still finishing. The guarantee is per process.

**Alternatives:** Caffeine `AsyncLoadingCache`, a striped/per-key lock, Guava-style loading cache, Redis lock, database advisory lock, or origin-side request collapsing.

**Failure cases:** Leader failure is shared; executor saturation rejects the candidate; timeout opens a future retry window; a caller already overlapping invalidation may receive the earlier value; multi-instance deployment can perform one load per instance.

**Important classes:** `SingleFlightStrategy`, `FutureSupport`, `GenerationTracker`, `ExecutorConfiguration`.

**Likely questions:** Why use a future instead of a lock? Why `remove(key, future)` instead of `remove(key)`? Does timeout cancel JDBC? How are different keys isolated? Can the map leak?

## 4. Stale-while-revalidate (SWR)

**What problem exists?** Blocking every request on a refresh protects freshness but creates latency spikes at expiration. Some data is useful even when slightly stale.

**Why does the naive implementation fail?** One deadline turns a usable value into a hard miss instantly. All callers wait and may stampede. Launching a background task for every stale request merely moves the herd to another executor.

**How does our implementation work?** `TimedEntry` stores `freshUntilNanos` and `staleUntilNanos`. Fresh reads are hits. A stale-but-servable read returns immediately and calls `startRefresh`; `putIfAbsent` allows one refresh future per key. After hard expiry, callers join the same future instead of receiving stale data. Refresh failure leaves the original entry untouched until its original hard deadline.

**Tradeoffs:** Lower tail latency and better availability at the cost of bounded staleness. The data owner must decide whether stale price/stock is acceptable. HerdShield exposes the policy rather than hiding it.

**Alternatives:** Synchronous refresh, `refreshAfterWrite`, scheduled refresh-ahead, stale-if-error, versioned reads, or no stale serving for correctness-critical data.

**Failure cases:** Continuous origin failure reaches hard expiry and then fails requests; a too-large stale window hides changes; executor rejection counts as refresh failure; an invalidation detaches the old refresh generation.

**Important classes:** `StaleWhileRevalidateStrategy`, `TimedEntry`, `StaleWhileRevalidateConcurrencyTest`.

**Likely questions:** What is the difference between fresh TTL and stale TTL? Why not extend stale time on failure? How do 100 stale requests create one refresh?

## 5. Cache avalanche and TTL jitter

**What problem exists?** Many independently keyed entries can expire in the same time bucket after bulk warm-up, deployment, or scheduled loading. The origin sees a burst even when no key is individually hot.

**Why does the naive implementation fail?** A fixed TTL preserves synchronization: entries written together expire together. Single-flight helps duplicate work for the same key but does not combine different keys.

**How does our implementation work?** `TtlJitterCalculator` mixes the key hash with an experiment seed and maps it uniformly into `[-jitter, +jitter]`. `TtlJitterStrategy` adds that offset to the base TTL and clamps the result to at least 1 ms. Avalanche experiments warm a key range and schedule each key just after its computed expiry, then report expected deadline buckets and actual peak origin-load buckets.

**Tradeoffs:** Expiry becomes less predictable per entry, and some entries live shorter or longer than the base TTL. Deterministic keyed jitter is repeatable but does not re-randomize every rewrite of the same key.

**Alternatives:** Positive-only random jitter, refresh-ahead, staggered warming, rate-limited reload, soft TTL, or event-driven invalidation.

**Failure cases:** Jitter smaller than the traffic bucket may not materially spread load; one hot key can still stampede; a restart empties an in-process cache regardless of TTL distribution.

**Important classes:** `TtlJitterCalculator`, `TtlJitterStrategy`, `ExperimentService.expirationDistribution`.

**Likely questions:** Why does jitter not solve a hot-key stampede? Why make the seed deterministic? Could negative jitter violate freshness?

## 6. Cache penetration and negative caching

**What problem exists?** Repeated lookup of nonexistent keys bypasses a positive cache forever and consumes origin capacity.

**Why does the naive implementation fail?** “Nothing” is represented by absence in both the cache and origin result, so the system cannot distinguish “not checked” from “checked and missing.”

**How does our implementation work?** `LoadResult<V>` explicitly contains either a value or an empty result. `NegativeCacheStrategy` caches both, but missing results use `negativeTtl`, not the positive TTL. A caught database failure never becomes `LoadResult.notFound()`.

**Tradeoffs:** A newly created record may remain hidden until the negative TTL ends unless the create/write path invalidates the key. Negative entries also consume bounded cache space.

**Alternatives:** Bloom filter, input/range validation, allowlist, request throttling, or origin-side existence indexes.

**Failure cases:** Initial concurrent misses can still stampede because this strategy isolates negative caching rather than combining it with single-flight; malicious traffic across unbounded unique keys can churn the bounded cache.

**Important classes:** `LoadResult`, `NegativeCacheStrategy`, `NegativeCacheStrategyTest`.

**Likely questions:** Why not cache `null`? Why is the negative TTL shorter? How would you combine negative caching with single-flight in production?

## 7. TTL and eviction

**What problem exists?** Cached values must not live forever, and memory must remain bounded.

**Why does the naive implementation fail?** An unbounded map leaks memory. Wall-clock comparisons can jump if system time changes. Treating TTL expiry and size eviction as the same thing obscures why an entry disappeared.

**How does our implementation work?** Strategy deadlines use `System.nanoTime()` through `TimeSource`, which is monotonic. Caffeine enforces `maximumSize`. Strategies decide freshness on lookup and remove expired entries. Tests inject `MutableTimeSource` to advance time without sleeping.

**Tradeoffs:** Manual metadata is more code than Caffeine’s built-in fixed expiry but makes two-deadline SWR and per-key jitter explicit. Expired entries may remain physically present until accessed or evicted, though they are never served as fresh.

**Alternatives:** Caffeine `expireAfterWrite`, custom `Expiry`, `refreshAfterWrite`, timer-wheel cleanup, or Redis TTL.

**Failure cases:** Poor TTL choice causes stale data or low hit rate; maximum-size churn can lower effectiveness; duration overflow is rejected/fails rather than silently wrapping.

**Important classes:** `CachePolicy`, `TimedEntry`, every strategy constructor.

**Likely questions:** TTL versus time-to-idle? Expiration versus eviction? Why `nanoTime` instead of `currentTimeMillis`?

## 8. Invalidation

**What problem exists?** PostgreSQL changes while cached copies remain. A write also can overlap an older origin load.

**Why does the naive implementation fail?** Merely deleting the current entry is insufficient: an older load can finish afterward and put its pre-write snapshot back.

**How does our implementation work?** `ProductService.updateProduct` performs `saveAndFlush` and invalidates the key in all strategies. Async strategies capture a `GenerationTracker.Token` before loading. Invalidation advances the key’s generation and detaches the in-flight mapping. A loader may populate only if its exact generation cell and global epoch still match. A post-put recheck closes the check/put invalidation race.

**Tradeoffs:** A read already in progress may still return the older snapshot; the policy prevents future cache pollution but is not a linearizable read/write protocol. Invalidating every strategy is appropriate for a lab but duplicates cache work.

**Alternatives:** Update cache after commit, transaction synchronization, versioned cache values, write-through, change-data capture, event invalidation, or short TTL only.

**Failure cases:** Process crash between DB commit and invalidation; multiple instances not receiving the invalidation; rollback after early invalidation causes only an extra miss, while updating the cache before commit could publish uncommitted data.

**Important classes:** `ProductService.updateProduct`, `CacheStrategyRegistry.invalidateAll`, `GenerationTracker`.

**Likely questions:** Is cache invalidation solved perfectly? What happens if invalidation races with load? Why invalidate after `saveAndFlush`?

## 9. `ConcurrentHashMap`

**What problem exists?** Many request threads need atomic ownership election without a global lock.

**Why does the naive implementation fail?** `get` then `put` is not atomic. `HashMap` itself is unsafe for concurrent mutation. Synchronizing the entire method serializes different keys.

**How does our implementation work?** `putIfAbsent` is the linearization point for an in-flight window. The map value is the coordination object. `remove(key, sameFuture)` is a conditional cleanup operation.

**Tradeoffs:** Atomic map operations protect map state, not arbitrary workflows around them. High unique-key concurrency still consumes one temporary entry per active key, so the executor and API are bounded.

**Alternatives:** `computeIfAbsent` with great care, striped locks, explicit lock objects, or Caffeine `AsyncCache`.

**Failure cases:** Running slow loader work inside `computeIfAbsent`; unconditional removal deleting a newer value; never removing exceptional futures.

**Important classes:** `SingleFlightStrategy.inFlight`, `StaleWhileRevalidateStrategy.refreshes`.

**Likely questions:** Is `ConcurrentHashMap` lock-free? What exactly is atomic? Why not use a map of `ReentrantLock`?

## 10. `CompletableFuture`

**What problem exists?** Followers need both the eventual value and the same exception outcome without each executing the loader.

**Why does the naive implementation fail?** A lock only controls entry; callers still need somewhere to publish the result and failure. Ad hoc wait/notify code is easier to deadlock and harder to time out.

**How does our implementation work?** The leader publishes an incomplete future first. Loader success calls `complete`; failure calls `completeExceptionally`; `orTimeout` completes exceptionally after the policy limit; `FutureSupport.join` unwraps the root cause into domain-level runtime exceptions.

**Tradeoffs:** `join` blocks MVC/experiment worker threads. That is intentional in a Spring MVC project, but a reactive application would compose futures rather than block. Future timeout does not necessarily interrupt underlying I/O.

**Alternatives:** `FutureTask`, `CountDownLatch` plus result holder, Reactor `Mono.cache`, Kotlin deferred, or structured concurrency scoped values.

**Failure cases:** Completing before populating the cache creates a tiny second miss window; populating without generation checks can publish stale data; ignoring exceptional completion can leave followers hanging.

**Important classes:** `FutureSupport`, `SingleFlightStrategy.loadAsLeader`, `StaleWhileRevalidateStrategy.doRefresh`.

**Likely questions:** `get` versus `join`? What thread executes completion callbacks? Does `orTimeout` cancel the supplier?

## 11. Race conditions and testing

**What problem exists?** Concurrency bugs are timing-dependent and may disappear under a debugger.

**Why does naive testing fail?** Looping 100 calls sequentially does not create overlap. Arbitrary sleeps make tests slow and flaky. Testing only final values misses duplicate origin work.

**How does our implementation work?** Tests use `CountDownLatch` to control entry and release, fixed-size caller pools, atomic counters for origin invocations, and `MutableTimeSource` for expiry. Assertions cover both value correctness and coordination-state cleanup.

**Tradeoffs:** Latch tests model selected interleavings rather than proving all possible schedules. They are deterministic enough for CI and directly test the promised invariants.

**Alternatives:** JCStress for Java memory-model exploration, Lincheck, property-based concurrent tests, JMH for microbenchmarks, or production chaos tests.

**Failure cases:** Releasing a loader before followers join can make a test nondeterministic; too-small pools can deadlock at a barrier; conflating 404 with system error can distort results.

**Important classes:** all tests under `backend/src/test/java/dev/herdshield/cache`, especially `SingleFlightConcurrencyTest`.

**Likely questions:** How do you make 100 threads arrive together? Why are latches better than sleep? What remains untested?

## 12. Memory-leak risks

**What problem exists?** Coordination state may outlive the request if a loader hangs or cleanup is skipped. Unique keys can also grow metadata maps.

**Why does the naive implementation fail?** A future inserted and never removed keeps the key, future, captured callbacks, and possibly result graph reachable. An unbounded generation map can grow even if the value cache is bounded.

**How does our implementation work?** In-flight futures have policy timeouts and unconditional `whenComplete` cleanup. Cleanup is conditional on the exact future. Loader executors and queues are bounded. Caffeine bounds value caches and the generation-cell cache. A global epoch invalidates all captured tokens on clear.

**Tradeoffs:** Generation-cell eviction conservatively prevents an older load from caching; under extreme key churn that may reduce hit rate but preserves correctness. Timed-out underlying I/O may continue temporarily outside the map.

**Alternatives:** Scheduled scavenger, maximum in-flight key count, bulkhead/semaphore, cancellation-aware driver, weak keys, or library-provided async cache.

**Failure cases:** Removing timeout, using an unbounded executor, attaching callbacks that capture large objects, or forgetting cleanup on rejection.

**Important classes:** `GenerationTracker`, both async strategies, `ExecutorConfiguration`.

**Likely questions:** Could single-flight leak memory? What if the loader never returns? What is bounded and what is only temporary?

## 13. Freshness versus availability

**What problem exists?** When the origin is slow or unavailable, the system must choose between current data, fast data, and any data.

**Why does a universal rule fail?** Stale inventory can oversell; stale product description may be harmless. The correct policy is domain-specific.

**How does our implementation work?** Strategy selection makes the choice explicit. Single-flight favors fresh-after-load and shares failure. SWR favors immediate bounded-stale responses. Negative caching favors origin protection for missing data. Hard deadlines prevent unlimited staleness.

**Tradeoffs:** Availability gains come from accepting bounded inconsistency. Short TTLs improve freshness but reduce hit rate and increase load. Long stale windows improve resilience but risk misleading users.

**Alternatives:** Conditional requests/version checks, quorum reads, circuit breaker plus stale-if-error, or bypass cache for correctness-critical fields.

**Failure cases:** Treating all product fields identically, hiding staleness from consumers, or using SWR where regulatory/current data is required.

**Important classes:** `CachePolicy`, `TimedEntry`, `ProductController` query controls.

**Likely questions:** Would you serve stale stock? How would you expose staleness to clients? What happens when the origin stays down past `staleUntil`?

## Short project walkthrough

1. Start at `CacheStrategy<K,V>` and `LoadResult<V>`.
2. Show the intentionally racy `NaiveCacheAsideStrategy`.
3. Explain the `putIfAbsent` linearization point in `SingleFlightStrategy`.
4. Show exact cleanup and generation checks.
5. Open the 100-caller tests.
6. Show `ExperimentService` start gate and metric deltas.
7. Run the Compare page and discuss the actual output without overstating it.
8. End with limitations: one process, bounded stale consistency, timeout not equal to I/O cancellation, and no fabricated benchmark claims.
