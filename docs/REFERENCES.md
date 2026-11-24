# References

These sources were reviewed for concepts and API semantics. HerdShield does not copy their architecture or code wholesale.

## Repositories and implementation discussions

1. [ben-manes/caffeine](https://github.com/ben-manes/caffeine) — studied size-bounded in-process caching, time-based expiration, asynchronous refresh, and cache statistics. HerdShield uses Caffeine as a bounded store but implements explicit teaching-oriented strategy behavior above it.
2. [Caffeine FAQ: recursive computations / async futures](https://github.com/ben-manes/caffeine/wiki/Faq) — studied the `putIfAbsent` future pattern and why installing a future before work lets concurrent callers share an in-flight result. HerdShield independently adds timeout cleanup, metrics, typed missing results, and invalidation generations.
3. [Caffeine issue #399: multiple async requests for the same key](https://github.com/ben-manes/caffeine/issues/399) — reviewed the real-world expectation that concurrent same-key callers should wait on one future and the integration pitfalls when annotations wrap the wrong boundary.
4. [Juferis/coin-cache-study](https://github.com/Juferis/coin-cache-study) — reviewed its taxonomy connecting stampede to single-flight/SWR, avalanche to TTL jitter, and penetration to negative caching. HerdShield intentionally omits its Redis HA and distributed-system scope.
5. [dtapuska/stale-while-revalidate](https://github.com/dtapuska/stale-while-revalidate) — reviewed the fresh/stale mental model and non-blocking revalidation behavior. HerdShield applies the concept to application data rather than browser HTTP caching.

## Primary documentation

6. [Spring Framework `CaffeineCache` API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/cache/caffeine/CaffeineCache.html) — checked the documented synchronized loader goal, `CompletableFuture` retrieval behavior, atomic `putIfAbsent`, and exception propagation expectations.
7. [RFC 5861 — HTTP Cache-Control Extensions for Stale Content](https://www.rfc-editor.org/rfc/rfc5861) — used as the terminology source for stale-while-revalidate and stale-if-error tradeoffs. HerdShield is not an HTTP intermediary cache.
8. [Spring Boot releases](https://github.com/spring-projects/spring-boot/releases) — selected the supported Spring Boot 3.5 line for a conservative Java 21 project rather than using the newest major solely for technology count.

## Independent design decisions

- Strategies share a small `CacheStrategy<K,V>` contract but maintain independent state so each can be explained and tested directly.
- Fixed-size application metrics replace a Prometheus/Grafana stack.
- Deterministic keyed jitter makes reruns comparable.
- Generation-guarded writes make the invalidation race explicit.
- PostgreSQL is always queried on an origin load; controlled latency is an optional addition, not the only simulated origin.
