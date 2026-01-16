# Production Failure Simulator (Spring Boot)

This project is a **safe, controlled lab** for training senior Java engineers to reproduce, observe, and fix common production failures.
Every failure is **explicitly triggered via HTTP endpoints**, bounded by **hard caps**, and designed to be safe on a personal laptop.

## ✅ Safe Runtime Defaults

**Recommended JVM flags (cap heap):**

```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xms128m -Xmx128m"
```

> **WARNING:** This project intentionally induces failures. Always run with the heap cap above.

## Architecture

- Java 17, Spring Boot 3.x, embedded Tomcat
- H2 in-memory DB (safe default)
- HikariCP with a **max pool size = 3** for pool exhaustion demos
- Per-failure package separation under `com.example.failuresim.failures.*`

## Failure Scenarios

Each entry lists:
- **Trigger** endpoint
- **Symptoms** to observe
- **Metrics/Logs** to watch
- **Typical Fix** in production

> All query params have **hard caps** enforced in code to prevent unbounded impact.

### 1) Heap OutOfMemoryError (Safe)
- **Trigger:** `POST /fail/oom/heap?entries=10&entryKb=64`
- **Symptoms:** Heap growth, GC thrash, potential OOM
- **Metrics/Logs:** Heap usage, GC logs, heap dump analysis
- **Fix:** Add eviction/TTL, remove static retention, use bounded caches
- **Notes:** Entries capped to protect laptops

### 2) Thread Pool Exhaustion
- **Trigger:** `POST /fail/threads/block?tasks=4&blockMs=3000`
- **Symptoms:** Hanging requests with low CPU
- **Metrics/Logs:** Thread pool metrics, queue size, p99 latency
- **Fix:** Timeouts, async IO, right-size pool, backpressure

### 3) DB Connection Pool Exhaustion
- **Trigger:** `POST /fail/db/slow?connections=4&holdMs=3000`
- **Symptoms:** Request timeouts, DB wait time
- **Metrics/Logs:** Hikari metrics, DB connection pool usage
- **Fix:** Optimize queries, set timeouts, close connections

### 4) Slow Downstream Dependency (No Timeout)
- **Trigger:** `GET /fail/downstream/slow?delayMs=2000`
- **Symptoms:** Requests blocked on slow downstream
- **Metrics/Logs:** Traces showing downstream span latency
- **Fix:** Timeouts, circuit breakers, bulkheads

### 5) Retry Storm (Safe)
- **Trigger:** `POST /fail/retry/storm?requests=3&maxRetries=2&failFirstAttempts=1`
- **Symptoms:** Amplified traffic, log noise
- **Metrics/Logs:** Retry counters, downstream RPS
- **Fix:** Cap retries, exponential backoff, jitter

### 6) GC Pressure (Non-OOM)
- **Trigger:** `POST /fail/gc/pressure?totalMb=32&chunkKb=256`
- **Symptoms:** p99 latency spikes, GC churn
- **Metrics/Logs:** GC logs, allocation rate
- **Fix:** Reduce allocations, reuse buffers

### 7) N+1 Query Explosion
- **Trigger:** `GET /fail/jpa/n-plus-one`
- **Symptoms:** Many SQL queries per request
- **Metrics/Logs:** Hibernate SQL logs, DB query count
- **Fix:** Fetch joins, batch fetching, DTO projections

### 8) Logging Overload
- **Trigger:** `POST /fail/logging/spam?lines=100&lineKb=4`
- **Symptoms:** Latency spikes, IO wait, log pipeline lag
- **Metrics/Logs:** Log throughput, disk IO
- **Fix:** Rate limit logs, async appenders, log levels

### 9) Configuration Drift / Missing Env Var
- **Trigger:** `GET /fail/config/missing`
- **Symptoms:** Feature works in staging but fails in prod
- **Metrics/Logs:** Config validation logs, startup errors
- **Fix:** Validate env vars on startup, CI checks

### 10) Partial Failure & Graceful Degradation
- **Trigger:** `GET /fail/feature/optional?failOptional=true`
- **Symptoms:** Optional data missing but core response still works
- **Metrics/Logs:** Degraded response counters
- **Fix:** Fallback logic, feature flags, partial responses

### 11) Time / Clock Bug
- **Trigger:** `GET /fail/time/zone?zone=America/Los_Angeles`
- **Symptoms:** Off-by-hours or DST issues
- **Metrics/Logs:** Scheduling anomalies, audit logs
- **Fix:** Store `Instant`, use `ZonedDateTime`

### 12) Serialization / Contract Break
- **Trigger:** `GET /fail/json/break?version=new`
- **Symptoms:** Clients fail to parse
- **Metrics/Logs:** Client error rates
- **Fix:** Version APIs, add fields without breaking old ones

### 13) Cache Poisoning / Stale Cache
- **Trigger:** `GET /fail/cache/stale?userId=42&locale=en-US`
- **Symptoms:** Users see wrong locale/content
- **Metrics/Logs:** Cache hit ratios, mismatch logs
- **Fix:** Correct cache keys, immutable values

### 14) Idempotency Failure
- **Trigger:** `POST /fail/idempotency/duplicate?requestId=abc&useIdempotencyKey=false`
- **Symptoms:** Duplicate orders/charges
- **Metrics/Logs:** Duplicate IDs, replay logs
- **Fix:** Require idempotency keys, dedupe storage

### 15) Async Consumer Lag (Simulated)
- **Trigger:** `POST /fail/queue/lag?enqueue=20&process=5&processMs=200`
- **Symptoms:** Backlog grows, delayed processing
- **Metrics/Logs:** Queue depth, consumer lag
- **Fix:** Scale consumers, backpressure

### 16) File Descriptor / Resource Leak (Safe)
- **Trigger:** `POST /fail/io/leak?open=5`
- **Symptoms:** "Too many open files" in real systems
- **Metrics/Logs:** OS open file descriptor count
- **Fix:** Try-with-resources, always close streams
- **Cleanup:** `POST /fail/io/leak?cleanup=true`

> **WARNING:** FD leak is capped at 50 streams to keep the host safe.

### 17) Cold Start / Warmup Problem
- **Trigger:** `GET /fail/startup/cold?warmupMs=1500`
- **Symptoms:** First request slow, next fast
- **Metrics/Logs:** Cold-start latency
- **Fix:** Warmup endpoints, precompute caches

### 18) Security Misconfiguration Impact
- **Trigger:** `GET /fail/security/cookie?secure=false&sameSite=None`
- **Symptoms:** Auth breaks in browsers
- **Metrics/Logs:** Browser warnings, auth error rate
- **Fix:** Use Secure with SameSite=None

### 19) Human Error Simulation
- **Trigger:** `GET /fail/human/misconfig`
- **Symptoms:** Feature disabled globally
- **Metrics/Logs:** Flag change audit logs
- **Fix:** Approval workflows, config validation

## Postmortem Template

See [POSTMORTEM.md](POSTMORTEM.md) for a production-grade incident template.

## Optional: Postgres

H2 is the safe default. If you want Postgres, set:

```
spring.datasource.url=jdbc:postgresql://localhost:5432/failures
spring.datasource.username=postgres
spring.datasource.password=postgres
```

## Safety Guarantees Recap

- Failures are **only triggered via endpoints**
- **No infinite loops** or unbounded memory growth
- **Heap capped** with recommended JVM flags
- **All limits capped** by hard-coded guardrails

