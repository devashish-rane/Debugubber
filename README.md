# Production Failure Simulator (Spring Boot)

A single, intentionally vulnerable-but-safe Spring Boot application used to **reproduce, observe, and fix** common production failures. Every failure is triggered via HTTP endpoints and bounded with hard caps, making it safe to run on a laptop.

> **WARNING**
> This project intentionally introduces failure modes. Only run it locally in a controlled environment.

## Safe JVM Run Command

```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xms128m -Xmx128m"
```

**Heap usage is capped via JVM flags.** Each endpoint also enforces hard caps to avoid runaway resource usage.

## How to Use

- Start the app.
- Call the endpoints below to trigger failures.
- Observe logs, metrics, and behavior as described.

You can also view basic telemetry at:
- `http://localhost:8080/actuator/metrics`
- `http://localhost:8080/actuator/threaddump`

## Failure Scenarios

Each scenario includes:
- **Trigger** (HTTP endpoint)
- **Expected symptoms**
- **Metrics/logs to check**
- **Typical production fix**

### 1) Heap OutOfMemoryError (Safe)
- **Trigger:** `POST /fail/oom/heap?entries=50&entryKb=64`
- **Symptoms:** Rising heap usage, GC pauses, eventual OOM if repeatedly invoked.
- **Metrics/logs:** JVM heap usage, GC pause time, OOM logs.
- **Fix:** Add cache eviction, size caps, and leak detection.

### 2) Thread Pool Exhaustion
- **Trigger:** `POST /fail/threads/block?tasks=5&blockMs=2000`
- **Symptoms:** Hung requests, high latency without CPU spike.
- **Metrics/logs:** Thread pool queue depth, request latency, thread dumps.
- **Fix:** Increase pool size carefully, isolate blocking work, add timeouts.

### 3) DB Connection Pool Exhaustion
- **Trigger:** `POST /fail/db/slow?concurrent=5&delayMs=2000`
- **Symptoms:** Threads waiting on DB connections, slow requests.
- **Metrics/logs:** Hikari active/idle connections, slow query logs.
- **Fix:** Tune pool size, optimize queries, add timeouts and backpressure.

### 4) Slow Downstream Dependency (No Timeout)
- **Trigger:** `GET /fail/downstream/slow?delayMs=2000`
- **Symptoms:** Request latency matches downstream delay, thread pool saturation.
- **Metrics/logs:** Client latency histograms, downstream SLA metrics.
- **Fix:** Add timeouts, circuit breakers, and fallbacks.

### 5) Retry Storm (Safe)
- **Trigger:** `POST /fail/retry/storm?retries=3&delayMs=250`
- **Symptoms:** Amplified traffic, spiky error rates.
- **Metrics/logs:** Retry counts, request volume, downstream errors.
- **Fix:** Cap retries, use exponential backoff + jitter, circuit breakers.

### 6) GC Pressure (Non-OOM)
- **Trigger:** `POST /fail/gc/pressure?batches=20&allocationKb=256`
- **Symptoms:** p99 latency spikes, throughput dips.
- **Metrics/logs:** GC pause times, allocation rate, latency percentiles.
- **Fix:** Reduce allocations, reuse buffers, tune GC.

### 7) N+1 Query Explosion
- **Trigger:** `GET /fail/jpa/n-plus-one`
- **Symptoms:** Excess SQL queries, DB CPU spikes.
- **Metrics/logs:** Hibernate SQL logs, DB query counts.
- **Fix:** Fetch joins, batch size, DTO projections.

### 8) Logging Overload
- **Trigger:** `POST /fail/logging/spam?lines=50`
- **Symptoms:** Slow requests, heavy I/O.
- **Metrics/logs:** Log throughput, I/O wait, request latency.
- **Fix:** Sample logs, reduce verbosity, async logging.

### 9) Configuration Drift / Missing Env Var
- **Trigger:** `GET /fail/config/missing`
- **Symptoms:** Works in one env, fails in another.
- **Metrics/logs:** Config validation logs, feature flag dashboards.
- **Fix:** Validate config on startup, enforce deployment checks.

### 10) Partial Failure & Graceful Degradation
- **Trigger:** `GET /fail/feature/optional`
- **Symptoms:** Partial response with warning instead of total failure.
- **Metrics/logs:** Dependency error rate, fallback usage.
- **Fix:** Design optional dependencies with graceful fallbacks.

### 11) Time / Clock Bug
- **Trigger:** `GET /fail/time/zone?localDateTime=2024-03-10T01:30:00`
- **Symptoms:** Off-by-hours timestamps around DST.
- **Metrics/logs:** Timestamp anomalies, scheduling errors.
- **Fix:** Use `Instant`/`ZonedDateTime`, store UTC.

### 12) Serialization / Contract Break
- **Trigger:** `GET /fail/json/break?version=new`
- **Symptoms:** Clients fail to deserialize field changes.
- **Metrics/logs:** Client error rates, schema validation errors.
- **Fix:** Version APIs, add fields without removing old ones.

### 13) Cache Poisoning / Stale Cache
- **Trigger:** `GET /fail/cache/stale?userId=42&region=us-east&segment=blue`
- **Symptoms:** Wrong cached data returned across regions.
- **Metrics/logs:** Cache hit anomalies, user reports.
- **Fix:** Include all cache key dimensions, avoid mutable values.

### 14) Idempotency Failure
- **Trigger:** `POST /fail/idempotency/duplicate?requestId=abc123`
- **Symptoms:** Duplicate processing (double charges, duplicate records).
- **Metrics/logs:** Duplicate transaction IDs, audit logs.
- **Fix:** Implement idempotency keys and deduplicate operations.

### 15) Async Consumer Lag (Simulated)
- **Trigger:** `POST /fail/queue/lag?items=50`
- **Symptoms:** Backlog grows, delayed processing.
- **Metrics/logs:** Queue depth, processing lag.
- **Fix:** Scale consumers, add backpressure, optimize processing.

### 16) File Descriptor / Resource Leak (Safe)
- **Trigger:** `POST /fail/io/leak?count=5`
- **Cleanup:** `POST /fail/io/leak?closeAll=true`
- **Symptoms:** “Too many open files” errors.
- **Metrics/logs:** Open file counts, I/O errors.
- **Fix:** Use try-with-resources, enforce leak detection.

### 17) Cold Start / Warmup Problem
- **Trigger:** `GET /fail/startup/cold?delayMs=2000`
- **Symptoms:** First request after deploy is slow.
- **Metrics/logs:** Cold-start latency spikes.
- **Fix:** Warm up caches, pre-load data, synthetic traffic.

### 18) Security Misconfiguration Impact
- **Trigger:** `GET /fail/security/cookie`
- **Symptoms:** Session cookie rejected in HTTPS-only environments.
- **Metrics/logs:** Auth failure rates, missing cookies.
- **Fix:** Set `Secure` when `SameSite=None` or adjust SameSite policy.

### 19) Human Error Simulation
- **Trigger:** `GET /fail/human/misconfig`
- **Symptoms:** Feature disabled with no code change.
- **Metrics/logs:** Config change audit logs, feature usage drop.
- **Fix:** Add validation and change approvals.

## Notes on Safety and Limits

- All endpoints are **explicitly triggered** and **bounded**.
- Hard caps prevent runaway memory, thread, or file usage.
- No scenario runs automatically at startup.

## Project Structure (High-Level)

```
src/main/java/com/example/failuresim
├── config
├── failures
│   ├── cache
│   ├── config
│   ├── db
│   ├── downstream
│   ├── feature
│   ├── gc
│   ├── human
│   ├── idempotency
│   ├── io
│   ├── jpa
│   ├── json
│   ├── logging
│   ├── oom
│   ├── queue
│   ├── retry
│   ├── security
│   ├── startup
│   ├── threads
│   └── time
└── resources
```

## Postmortem Template

See [POSTMORTEM.md](POSTMORTEM.md) for a standard incident write-up template.
