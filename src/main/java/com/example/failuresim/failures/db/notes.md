Got it. Below is a **long, production-grade runbook**, **pure `.md`**, inline response (NOT canvas), written so you can literally keep this as your **go-to guide during real incidents**.

This is intentionally detailed, slow, and explicit — because in prod your brain is already on fire 🔥🧠.

---

```md
# 🚨 RUNBOOK: JDBC / HikariCP Connection Pool Exhaustion

## Scenario Summary

**Incident Type**  
Database connection pool exhaustion causing request hangs and HTTP 5xx errors.

**Typical Stack**
- Java / Spring Boot
- JDBC (blocking)
- HikariCP
- Tomcat (`http-nio-*`)
- Relational DB (Postgres / MySQL / Oracle)

---

## 1️⃣ What This Failure IS (and IS NOT)

### ✅ What it IS
- Threads waiting too long for a DB connection
- HikariCP pool fully occupied
- Requests block and eventually fail with 500 / 503
- CPU often LOW, memory looks fine

### ❌ What it is NOT
- DB crash
- SQL syntax error
- Network outage
- Thread leak
- JVM OOM

This is **resource starvation**, not system failure.

---

## 2️⃣ Canonical Error Signature (Log Pattern)

### Application Log
```

CannotGetJdbcConnectionException: Failed to obtain JDBC Connection

```

### Root Cause (Hikari)
```

SQLTransientConnectionException:
Connection is not available, request timed out after 30000ms
(total=10, active=10, idle=0, waiting=0)

```

### What This Line Means (CRITICAL)
| Field | Meaning |
|-----|--------|
| total | Max pool size |
| active | Connections currently in use |
| idle | Free connections |
| waiting | Threads waiting at that instant |

If `active == total`, the pool is exhausted.

---

## 3️⃣ Immediate User-Visible Symptoms

- Requests hang for **30s** (default timeout)
- Sudden spike in latency
- Burst of HTTP 500 / 503
- Other unrelated endpoints also slow
- ALB / Gateway may return 504
- Retry storms worsen the situation

---

## 4️⃣ Thread Model (THIS CONFUSION CAUSES 80% MISDIAGNOSIS)

### Key Truth
**There is NO DB thread pool.**

JDBC is **blocking**.

The calling thread:
- waits for connection
- executes SQL
- waits for response
- releases connection

### Threads You Will See

#### HTTP Threads
```

http-nio-8080-exec-*

```
- One per concurrent request
- Blocked if downstream work blocks

#### Executor Threads (if used)
```

pool-1-thread-*

```
- Created by your code
- Often block on DB

---

## 5️⃣ How This Failure Happens (Timeline)

Example:
- Pool size = 10
- Each request uses = 5 DB calls
- Concurrent HTTP requests = 5

### Math:
```

Demand = 5 × 5 = 25 connections
Supply = 10
Shortage = 15

```

### Timeline:
1. First threads grab DB connections
2. Pool becomes empty
3. Remaining threads wait
4. `connectionTimeout` exceeded
5. Hikari throws exception
6. Spring converts to 500
7. All waiting requests fail together

This is **deterministic**, not random.

---

## 6️⃣ FIRST 60-SECOND CHECKS (ON-CALL PLAYBOOK)

### 6.1 Check Logs
Search for:
```

Connection is not available
HikariPool
CannotGetJdbcConnectionException

````

---

### 6.2 Take Thread Dump (MOST IMPORTANT)

```bash
jcmd <pid> Thread.print
````

#### Patterns to Look For

##### Waiting for DB connection

```
TIMED_WAITING
at com.zaxxer.hikari.pool.HikariPool.getConnection
```

##### Actively executing DB query

```
RUNNABLE
at java.net.SocketInputStream.read
```

##### HTTP thread blocked waiting for DB work

```
WAITING
at java.util.concurrent.CompletableFuture.join
```

If you see MANY threads in these states → confirmed pool exhaustion.

---

### 6.3 Check Metrics (If Available)

```
hikaricp.connections.active == max
hikaricp.connections.idle == 0
hikaricp.connections.pending > 0
```

---

## 7️⃣ Why ONE Request May Succeed (and All Others Fail)

Connections are granted to **threads**, not requests.

* No fairness
* No request isolation
* Whoever arrives first wins

With different timing:

* 1 request may succeed
* OR 0 requests may succeed
* OR all requests fail together

Success is **timing-dependent**, not guaranteed.

---

## 8️⃣ Common Real-World Causes

* Parallel streams calling repositories
* `@Async` with JDBC
* Executors inside controllers
* Batch endpoints
* Retry storms
* N+1 queries under load
* Slow SQL holding connections
* DB network latency
* Pool too small for request shape

---

## 9️⃣ Emergency Mitigations (SAFE + REVERSIBLE)

### 🛑 Immediate

* Disable offending endpoint / feature flag
* Reduce incoming traffic
* Scale down concurrency at LB

### 🧯 Stabilization

* Restart pods (frees stuck connections)
* Temporarily increase pool size (ONLY IF DB CAN HANDLE IT)
* Reduce retries

⚠️ Increasing pool size blindly can kill the DB.

---

## 🔧 Tactical Fixes (Code / Config)

### 10.1 Fail Fast

```properties
spring.datasource.hikari.connectionTimeout=3000
```

Better to fail in 3s than kill the system in 30s.

---

### 10.2 Remove Parallelism in Request Path

**NEVER** do:

* Executors inside controllers
* Parallel DB calls per request

---

### 10.3 Cap DB Concurrency Explicitly

Use semaphores / bulkheads before DB calls.

---

### 10.4 Set Query Timeouts

Ensure connections are not held indefinitely.

---

## 🧠 Strategic Fixes (Senior-Level)

* Per-workload DB pools
* Queue-based async processing
* Read replicas for heavy reads
* Separate batch workloads
* Backpressure at API boundary
* Circuit breakers before DB
* Capacity math reviewed during design

---

## 🔬 Debug Checklist (When Calm)

* Can one request exhaust the pool?
* Does `poolSize < perRequestDBUsage`?
* Are retries amplifying load?
* Are timeouts aligned (HTTP < DB < LB)?
* Are slow queries logged?
* Is pool size tied to DB max connections?

---

## 🚫 Anti-Patterns (Guaranteed Outage Generators)

* Parallel JDBC calls
* Blocking JDBC in async executors
* Unlimited concurrency
* “Let’s just increase pool size”
* Relying on retries
* Ignoring `connectionTimeout`

---

## 🧩 Golden Rule (Tattoo This)

> **JDBC does not scale with threads.
> It scales with restraint.**

---

## 🧠 Final Mental Model

* Threads = people
* DB connections = chairs
* Pool exhaustion = too many people standing
* Timeouts = people leaving angry

No chairs broke.
Too many people.

---

## 📌 Incident Closing Statement Template

> Root cause was JDBC connection pool exhaustion due to concurrent requests performing parallel blocking DB calls. Pool saturation caused threads to wait beyond connection timeout, resulting in HTTP 5xx responses. No database outage occurred. Mitigations include concurrency reduction, pool tuning, and architectural changes to prevent per-request parallel JDBC usage.


