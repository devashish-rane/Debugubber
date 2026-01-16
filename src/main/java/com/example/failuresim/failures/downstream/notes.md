````md
# 🚨 RUNBOOK: Downstream API Slowness / Failures (Spring Boot / Tomcat, production-fast debugging)

## Goal
Detect, confirm, mitigate, and permanently fix incidents where **your API latency/5xx spikes because a downstream dependency is slow or failing**.

This is the “service-to-service call made prod sad” runbook.

---

## 0) The core mental model (memorize this)
### Synchronous dependency rule
> If your endpoint synchronously calls a downstream service, then:  
**Upstream latency ≈ downstream latency (+ overhead)**  
And under concurrency: **threads get held**, which can snowball into outages.

### Two failure modes
1) **Slow but progressing**: requests complete eventually → high latency, but low error rate.
2) **Stuck / resource-starved**: requests don’t progress (timeouts, pool exhaustion) → error spikes, queueing, retries amplify.

---

## 1) Symptoms you will see in prod
### User-facing
- Latency p95/p99 jumps sharply (often matches downstream p95/p99)
- Increase in 504s (LB timeout) or 5xx (app timeouts)
- Some endpoints degrade, others remain fine (depends on dependency graph)
- “Works locally but slow in prod” pattern

### Service telemetry signals (ideal)
- Outbound HTTP client metrics show latency spike: `dependency=downstream-service`
- Increased `timeout` / `connect timeout` / `read timeout` exceptions
- Increased concurrent in-flight requests

### Logs (common strings)
- `Read timed out`
- `Connect timed out`
- `Connection refused`
- `UnknownHostException`
- `Broken pipe` (often client aborts before you respond)
- `HttpClientErrorException` / `HttpServerErrorException` (4xx/5xx from downstream)

---

## 2) First 60 seconds: triage checklist
### 2.1 Confirm it is dependency-driven
- Compare **your service latency** vs **downstream latency** (if you have dependency metrics)
- If your p99 ≈ downstream p99 → dependency is likely the cause.

### 2.2 Identify which dependency
Use the fastest available:
- APM traces (best) → top slow span is dependency call
- Access logs with request duration + route
- Outbound client logs (if enabled)

### 2.3 Check blast radius
- Is it one endpoint or all?
- Is it one downstream or multiple?
- Is it only one AZ / region / node pool?

### 2.4 Decide severity
- SEV1 if revenue/critical path, mass timeouts, retries storm.
- SEV2 if partial degradation, recoverable.
- SEV3 if isolated or low traffic.

---

## 3) Proving it quickly (no APM required)
### 3.1 Thread dump: the truth serum
Take a thread dump while incident is active:

```bash
jcmd <pid> Thread.print
````

#### What to look for

**If upstream is blocked on downstream HTTP call**, you’ll see stacks containing:

* `java.net.SocketInputStream.read`
* `sun.nio.ch.SocketDispatcher.read0`
* your HTTP client stack (OkHttp / Apache HttpClient / RestTemplate / WebClient blocking)
* your controller handler methods

**Interpretation**

* Many `http-nio-8080-exec-*` threads in those stacks = downstream call is occupying request threads.

### 3.2 Count “in-flight” work

If Tomcat metrics exist:

* `/actuator/metrics/tomcat.threads.busy`
* If busy threads climbs toward max threads, you’re approaching thread starvation.

---

## 4) The most common real root causes (downstream side)

* Downstream DB pool exhaustion
* Downstream CPU saturation / GC pauses
* Downstream overload (traffic spike)
* Network path issues (packet loss, DNS, TLS handshake problems)
* Misconfigured autoscaling (not enough replicas)
* Deployment regression (slow code path / new N+1)
* Retry storm from upstreams
* Rate limits / throttling

---

## 5) The upstream-side causes that *amplify* downstream slowness

These often turn “slowness” into “outage”:

### 5.1 Missing timeouts (deadly)

If you don’t set **connect timeout + read timeout**, requests can hang forever and consume threads.

### 5.2 Unbounded concurrency (no bulkhead)

If every request can call downstream, and you accept unlimited concurrency, you can overwhelm both services.

### 5.3 Retries without budgets (retry storms)

Retrying 3 times under load can turn:

* 100 RPS into 300 RPS
  and crush downstream.

### 5.4 Connection pool starvation (HTTP client side)

If your outbound HTTP client has a small connection pool or max connections per host, threads can block **waiting for a connection**, not even sending requests.

---

## 6) Mitigation playbook (safe + reversible actions)

### 6.1 Fail fast & protect your service

If dependency is unhealthy:

* Lower dependency timeouts (temporary) to reduce thread occupancy
* Return degraded response quickly
* Shed load via rate limiting / queueing

### 6.2 Circuit breaker / feature flag

If available:

* open circuit for the dependency
* serve cached / fallback response
* disable the feature path

### 6.3 Reduce upstream traffic

* LB rules / rate limit
* temporarily disable high-traffic endpoints
* scale your service *only if it helps* (it may just increase downstream load)

### 6.4 Reduce retry intensity

* reduce max attempts
* add jitter + exponential backoff
* enable retry budgets (see fixes section)

### 6.5 If you own downstream too

* scale downstream replicas
* scale DB connection capacity
* roll back recent deploy
* temporarily raise downstream pool sizes carefully

---

## 7) “Fast diagnosis” decision tree

### Case A: Your service latency ~ downstream latency and threads look normal

Likely: downstream is slow but progressing.
Mitigate by:

* timeouts + fallback
* caching
* async decoupling if needed

### Case B: Many request threads blocked, timeouts & 5xx rising

Likely: thread starvation / queue buildup.
Mitigate by:

* strict timeouts
* circuit breaker
* bulkhead (cap concurrent downstream calls)
* reduce retries

### Case C: Many threads blocked WAITING for outbound client connection pool

Look for stacks around:

* `PoolingHttpClientConnectionManager`
* `ConnectionPoolTimeoutException`
  Mitigate by:
* increase HTTP client connection pool
* cap concurrency
* reuse clients properly (don’t create per request)

---

## 8) Permanent fixes (the “never again” section)

### 8.1 Mandatory timeouts (non-negotiable)

For every outbound call:

* connect timeout (fast)
* read timeout (bounded)
* overall deadline (budget)
* per-route time budget based on SLO

Rule of thumb:

* connect: 200–500ms (in-region)
* read: 1–3s (depends on dependency)
* overall: < your LB timeout
  Align: **Client timeout < Server timeout < LB timeout** to avoid broken pipes.

### 8.2 Bulkheads (concurrency caps) — the outage killer

Cap concurrent calls per downstream:

* semaphore / thread pool isolation
* per-route caps
* per-tenant caps (if multi-tenant)

This prevents one dependency from consuming all request threads.

### 8.3 Circuit breaker + fallback

Trip on:

* high error rate
* high latency
* timeouts
  Recover with half-open probing.

Fallback strategies:

* cached last known good response
* partial response
* default behavior (“safe mode”)
* enqueue for async processing

### 8.4 Retry budgets (retry like an adult)

Retries must be:

* limited
* jittered
* conditional (only on transient errors)
* budgeted so retries don’t exceed, say, 5–10% of total traffic

Never retry:

* non-idempotent operations unless you have idempotency keys
* validation errors (4xx)
* known overload signals

### 8.5 Idempotency keys for write operations

If you retry POST/PUT:

* require an idempotency key
* prevent double writes

### 8.6 Observability upgrades (so future you doesn’t suffer)

Minimum:

* correlation IDs across services
* dependency latency histogram per route + status code
* timeout counters
* circuit breaker state metric
* in-flight request gauge
* “downstream failure reason” tag (timeout/connect/refused/5xx)

Best:

* distributed tracing with span tags:

  * `dependency.name`
  * `http.status_code`
  * `error.type`
  * `duration_ms`

---

## 9) Practical “prod-fast” investigation steps (scripted)

### 9.1 Confirm current concurrency & thread usage

```bash
jcmd <pid> Thread.print | grep -c "http-nio-8080-exec"
```

### 9.2 Find threads stuck in downstream socket reads

```bash
jcmd <pid> Thread.print | grep -n "SocketDispatcher.read0" -n
```

### 9.3 Search for timeouts in logs (fast)

Look for:

* `Read timed out`
* `Connect timed out`
* `Connection refused`
* `UnknownHostException`
* `Broken pipe`
* `TimeoutException`

---

## 10) “What to say in incident updates” (copy-paste templates)

### Update 1 (triage)

> We are seeing elevated latency and intermittent 5xx. Initial indicators point to downstream dependency `<X>` experiencing increased response times/timeouts. We are applying mitigations to reduce impact (timeouts + circuit breaker + reduced retries). Next update in ~15 minutes with confirmation and recovery progress.

### Update 2 (mitigation)

> Mitigation applied: capped concurrent calls to `<X>`, enabled fallback for `<route>`, reduced retry attempts. Latency is stabilizing and error rate is trending down. Working with downstream team on root cause (capacity / regression / DB contention).

### Post-incident root cause statement

> Root cause: synchronous upstream request path blocked on slow downstream calls, increasing in-flight concurrency and consuming request threads. Insufficient timeouts/bulkheads amplified impact. Fixes: enforce deadlines, add circuit breaker + bulkhead, improve dependency telemetry, and adjust retry strategy.

---

## 11) Common “gotchas” (these bite experienced engineers too)

* “NIO” thread names do not mean non-blocking app logic — your handler threads can still block.
* JVM `RUNNABLE` can include socket waiting; don’t assume high CPU.
* Scaling upstream can worsen downstream overload.
* Retrying can multiply traffic and deepen the outage.
* Broken pipe often means **client gave up** before you finished responding.

---

## 12) A simple golden config alignment (baseline)

* LB timeout: 60s
* Upstream request timeout: 3–10s (route-specific)
* Downstream read timeout: 1–5s (route-specific)
* Circuit breaker open after: high p95 latency or timeout spike
* Bulkhead: cap concurrency per downstream based on pool and CPU

---

## 13) Quick “production readiness” checklist for any downstream call

* [ ] connect/read/overall timeouts set
* [ ] circuit breaker + fallback exists
* [ ] concurrency capped (bulkhead)
* [ ] retries: limited + jitter + budget
* [ ] metrics: latency/error/timeout per dependency
* [ ] tracing with correlation ID
* [ ] dashboards & alerting set
* [ ] load test the dependency path

---

