# Benchmark Results

## Integrity rule

This file intentionally contains **no prefilled performance results**. The repository construction environment did not provide a runnable Docker daemon, Maven installation, Java 21 runtime, or PostgreSQL service, so inserting numbers here would be fabrication.

Use the procedure below on the target machine. Commit the raw JSON alongside any values copied into this document.

## Reproduction procedure

```bash
cp .env.example .env
docker compose up --build -d
docker compose ps
curl --fail http://localhost:8080/actuator/health
./scripts/run-benchmarks.sh
```

Run at least five complete repetitions. Do not run other experiments or reset metrics during a benchmark. Keep the same Compose configuration, request count, concurrency, key, and origin mode when comparing naive with single-flight.

Optional environment overrides:

```bash
HERDSHIELD_API_BASE=http://localhost:8080 \
HERDSHIELD_RESULTS_DIR=benchmark-results \
./scripts/run-benchmarks.sh
```

## Environment record

Fill this once per benchmark session.

| Field | Measured value |
| --- | --- |
| Date/time (UTC) | `<record after run>` |
| OS | `<record after run>` |
| CPU model / allocated cores | `<record after run>` |
| RAM / Docker memory limit | `<record after run>` |
| Java version (`docker compose exec backend java -version`) | `<record after run>` |
| PostgreSQL version (`docker compose exec postgres postgres --version`) | `<record after run>` |
| Docker version | `<record after run>` |
| Git commit | `<record after run>` |
| DB pool size | `<record after run>` |
| Cache loader threads | `<record after run>` |

## A. Naive stampede

Workload supplied by `run-benchmarks.sh`: one cold key, 500 requests, 100 concurrent workers, database query plus 75 ms controlled latency.

| Run | Requests | Concurrency | Origin latency | Median / p50 | p95 | p99 | Origin loads | Errors | Raw file |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 1 | `<actual>` | `<actual>` | `<actual>` | `<actual>` | `<actual>` | `<actual>` | `<actual>` | `<actual>` | `<path>` |
| 2–5 | `<repeat>` |  |  |  |  |  |  |  |  |

Median across runs: `<calculate from actual run values>`

## B. Single-flight

Use exactly the same workload and environment as section A.

| Run | Requests | Concurrency | Origin latency | Median / p50 | p95 | p99 | Origin loads | Coalesced | Errors | Raw file |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 1 | `<actual>` | `<actual>` | `<actual>` | `<actual>` | `<actual>` | `<actual>` | `<actual>` | `<actual>` | `<actual>` | `<path>` |
| 2–5 | `<repeat>` |  |  |  |  |  |  |  |  |  |

Median across runs: `<calculate from actual run values>`

### Primary same-workload comparison

| Metric | Naive (actual median) | Single-flight (actual median) | Direct difference |
| --- | ---: | ---: | ---: |
| Origin loads | `<actual>` | `<actual>` | `<subtract>` |
| p50 latency | `<actual>` | `<actual>` | `<subtract>` |
| p95 latency | `<actual>` | `<actual>` | `<subtract>` |
| Errors | `<actual>` | `<actual>` | `<subtract>` |

Do not add a percentage unless it is calculated from the values in this table and the calculation is shown.

## C. Stale-while-revalidate

The script warms one key, waits past `freshUntil` but stays inside `staleUntil`, then releases 500 requests.

| Run | Requests | Concurrency | Fresh TTL | Stale TTL | Stale responses | Background refreshes | Refresh failures | p95 | Origin loads |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1–5 | `<actual>` | `<actual>` | `<actual>` | `<actual>` | `<actual>` | `<actual>` | `<actual>` | `<actual>` | `<actual>` |

## D. Negative caching

The script sends 500 sequential requests for missing key `999999`. Concurrency is intentionally one so the benchmark isolates penetration from first-miss stampede behavior.

| Run | Requests | Negative TTL | Not-found responses | Negative-cache hits | Origin loads | Errors | p95 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1–5 | `<actual>` | `<actual>` | `<actual>` | `<actual>` | `<actual>` | `<actual>` | `<actual>` |

## E. TTL jitter / avalanche

Both cases warm 200 keys and schedule one request just after each key’s computed expiry. Fixed TTL and jittered TTL must use the same hardware, key range, request count, bucket size, and real-database origin mode.

| Run | Strategy | Keys | Base TTL | Jitter | Bucket size | Expiration buckets used | Peak loads/bucket | Peak origin queries/sec | Errors | Raw file |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 1–5 | Fixed / `NAIVE_CACHE_ASIDE` | `<actual>` | `<actual>` | 0 | `<actual>` | `<actual>` | `<actual>` | `<actual>` | `<actual>` | `<path>` |
| 1–5 | `TTL_JITTER` | `<actual>` | `<actual>` | `<actual>` | `<actual>` | `<actual>` | `<actual>` | `<actual>` | `<actual>` | `<path>` |

## Interpretation checklist

- Confirm raw JSON strategy and parameters before comparing files.
- Separate expected 404 responses from errors.
- Explain origin pool saturation if it affects tail latency.
- Treat controlled latency as a workload input, not a result.
- Report variance across runs; do not select only the best run.
- If the single-flight origin count is above one, inspect load timeout, key selection, cache clears, and whether requests truly overlapped before making a claim.
