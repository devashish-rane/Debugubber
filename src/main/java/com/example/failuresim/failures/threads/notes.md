
````md
# Thread Exhaustion & Request Latency — Production Runbook (Java / Spring Boot / Tomcat)

> **Goal**: A practical, production-grade guide to **identify, debug, mitigate, and prevent** issues related to **threads, request latency, thread-pool exhaustion, client timeouts, broken pipes**, and Spring/Tomcat async edge-cases.  
> **Tools focus**: `jcmd`, JFR/JMC, thread dumps, reproducible dev drills.

---

## 0) TL;DR (If you’re on-call and things are on fire)

1. **Confirm symptoms**: latency spike + hanging requests + rising timeout errors.
2. **Take evidence immediately**:
   - `jcmd <pid> Thread.print > dump1.txt`
   - wait 10s
   - `jcmd <pid> Thread.print > dump2.txt`
3. **Classify from thread dumps**:
   - Many `http-nio-*` blocked inside app code (e.g., `Future.get`) ⇒ request-thread starvation.
   - Executor threads all blocked (sleep/I/O/lock) + queue growth ⇒ executor starvation.
   - Many `BLOCKED` ⇒ lock contention.
4. **Mitigate safely**:
   - Shed load / rate-limit / disable the expensive endpoint.
   - Reduce concurrency (bulkhead/semaphore).
   - Fail fast before clients timeout (timeouts/circuit breakers).
5. **Prevent**:
   - Don’t block request threads.
   - Bound queues/concurrency.
   - Set timeouts and cancel work when clients disconnect.
   - Name your thread pools.

---

## 1) Mental Models (Read once, use forever)

### 1.1 The central truth
**Thread exhaustion converts time into damage.**  
Latency is the first symptom; timeouts, retries, broken pipes, and 5xx are the cascade.

### 1.2 What “thread exhaustion” really means
Not “too many threads” or “CPU is high.”  
It means **threads exist but aren’t available to do new work** because they’re blocked.

### 1.3 Why it’s dangerous
- CPU can be normal, memory can look fine.
- The service still becomes unusable because **progress stops**.
- Retries amplify load and spread failure to neighbors.

---

## 2) Thread taxonomy (Know what you’re looking at)

### 2.1 Tomcat request threads (Servlet model)
Typical names:
- `http-nio-8080-exec-*`

These threads:
- handle requests
- are reused
- spend most of their life **WAITING** when idle (that’s healthy).

### 2.2 Your executors (application work)
Typical names:
- `pool-1-thread-1` (default)
- `smallBlockingExecutor-2` (custom prefix)

These threads:
- run your submitted tasks
- are usually the bottleneck (small pool + blocking work).

### 2.3 JVM/system threads (mostly noise)
Examples:
- `GC Thread`, `VM Thread`, `Reference Handler`, `Finalizer`
Not usually your root cause unless JVM itself is unhealthy.

---

## 3) Thread states: what they mean (and what they *don’t*)

### 3.1 Core states you’ll see
| State | Meaning |
|---|---|
| RUNNABLE | Running or blocked in native I/O (surprise) |
| WAITING | Waiting indefinitely (future/lock/queue) |
| TIMED_WAITING | Waiting with timeout (sleep/park/wait(timeout)) |
| BLOCKED | Waiting to enter `synchronized` monitor |

### 3.2 Crucial rule: State alone is insufficient
> **WAITING alone is meaningless.**  
> **WAITING + stack trace + time correlation is truth.**

### 3.3 Healthy vs unhealthy WAITING (Tomcat)
**Healthy idle request thread**:
- stack shows:
  - `org.apache.tomcat.util.threads.TaskQueue.take()`

Meaning:
- no work currently; thread is waiting for the next request (normal).

**Unhealthy blocked request thread**:
- stack shows:
  - `java.util.concurrent.FutureTask.get()`
  - your controller method

Meaning:
- request can’t complete → clients wait → latency grows.

---

## 4) Your “/block” endpoint: why it can exhaust threads

Your pattern:
- Controller thread submits tasks to a **small executor**
- Each task blocks (e.g., `Thread.sleep(blockMs)`)
- Controller blocks on `future.get()`

This couples:
- request threads (Tomcat) ⇄ executor threads (your pool)

So under concurrency:
- executor saturates
- request threads block waiting on executor
- requests pile up
- clients time out and disconnect
- broken pipes and async request errors appear

---

## 5) Reproducing thread exhaustion locally (the right way)

### 5.1 The missing ingredient is concurrency
> Sequential load cannot exhaust request threads.

If you run `curl` in a normal loop without backgrounding, you’re sequential.

### 5.2 Correct parallel request generator (zsh-safe)
Your endpoint is `@PostMapping`, so use POST.
Also zsh treats `?` as glob → **quote the URL**.

```bash
for i in {1..10}; do
  curl -X POST "http://localhost:8080/fail/threads/block?tasks=10&blockMs=20000" &
done
wait
````

Expected outcomes during the 20s window:

* executor threads show `TIMED_WAITING (sleeping)`
* more `http-nio-*` threads become busy/blocked
* latency spikes
* some clients may disconnect ⇒ broken pipe logs

### 5.3 Common gotchas

* `zsh: no matches found` ⇒ you forgot quotes.
* `GET not supported` ⇒ you forgot `-X POST` for `@PostMapping`.
* Only 1 NIO thread busy ⇒ your requests aren’t actually concurrent (no `&`, no `-c` tool, same client connection behavior).

---

## 6) Production-grade tooling and commands (jcmd first)

### 6.1 Find PID

```bash
jcmd
```

### 6.2 Thread dump (safe in prod)

```bash
jcmd <PID> Thread.print > threaddump_$(date +%s).txt
```

### 6.3 Thread state summary (concise “stats”)

```bash
jcmd <PID> Thread.print | grep "java.lang.Thread.State" | sort | uniq -c
```

### 6.4 Name + state quick view

```bash
jcmd <PID> Thread.print \
| egrep '^(\"| *java.lang.Thread.State)' \
| paste - -
```

### 6.5 Two-dump comparison (detect stuckness)

```bash
jcmd <PID> Thread.print > dump1.txt
sleep 10
jcmd <PID> Thread.print > dump2.txt
diff dump1.txt dump2.txt
```

If the same stacks persist across dumps → starvation/lock/slow downstream.

---

## 7) JFR/JMC workflow (time-based truth)

### 7.1 Start recording

```bash
jcmd <PID> JFR.start name=threads settings=profile
```

### 7.2 Reproduce issue (load, block, etc.)

### 7.3 Dump & stop

```bash
jcmd <PID> JFR.dump name=threads filename=threads.jfr
jcmd <PID> JFR.stop name=threads
```

### 7.4 In JMC, use the right view

* **Threads → Thread Activity** (most important)
* Don’t rely only on the static thread list table.

Key: Scrub the timeline during the incident window.

---

## 8) How to *prove* thread exhaustion (not just suspect it)

Thread exhaustion is confirmed when **all** are true:

1. **Executor saturation**: executor threads have no idle gaps (sleeping/blocked continuously).
2. **Request-thread blocking**: `http-nio-*` threads blocked *inside app code* (e.g., `Future.get`, lock, downstream call).
3. **User-visible impact**: latency spikes, timeouts, hanging requests.
4. **Correlation over time**: the executor saturation window overlaps request-thread blocking.

If you only have (1) without (2), you may just have slow background work, not request-thread exhaustion.

---

## 9) What happens when latency becomes too high (the cascade)

### 9.1 Immediate effects

* p95/p99 latency explodes
* throughput drops
* queue lengths rise

### 9.2 Clients time out and disconnect

* browsers, mobile apps, service clients, load balancers.
* this triggers retry storms if retries are enabled.

### 9.3 Retry storm (the silent killer)

Retries multiply traffic:

* more requests
* more blocked threads
* more timeouts
* more retries
  Positive feedback loop.

### 9.4 Downstream and upstream blast radius

* upstream sees 502/504/timeouts
* health checks fail
* traffic shifts to other instances → they fail too

### 9.5 Secondary memory/GC impact

Blocked requests keep objects alive:

* more allocations retained
* GC pressure increases
* GC pauses further increase latency
* possible OOM as a **secondary** effect

---

## 10) Broken pipe: what it is and what to do

### 10.1 Meaning

`java.io.IOException: Broken pipe` happens when:

* client already closed the TCP connection
* server tries to write/flush the response

### 10.2 Does client disconnect always become “Broken pipe”?

No.

* It becomes broken pipe only when server writes after disconnect.
* Sometimes you see `ClientAbortException` or other IO exceptions.
* Sometimes nothing is written (client just times out).

### 10.3 Do we “handle” broken pipe?

Usually **no** (you can’t send a response to a closed socket).
Treat it as:

* a symptom that clients timed out
* a latency indicator

Logging guidance:

* avoid alerting on single occurrences
* treat spikes as “latency too high / timeouts”

---

## 11) `AsyncRequestNotUsableException` (Spring) — causes & meaning

### 11.1 Meaning

Spring tried to use request/response objects that are no longer valid.

### 11.2 Common causes

1. **Client timed out and disconnected** while server still processing.
2. **Async request timeout** expired (Spring MVC async processing).
3. **Response already committed** then another write attempted.
4. **Background thread tries to write** after request lifecycle ended.
5. Container cleanup after abort.

### 11.3 Can it cause HTTP 500?

It can, depending on timing:

* If headers not committed, framework might map it to 500.
* Often the client receives no status (connection already closed), so logs ≠ client reality.

---

## 12) How to interpret Tomcat NIO threads “always WAITING”

This is normal at rest.
`http-nio-*` threads usually WAIT on:

* `TaskQueue.take()`

That’s a **healthy idle worker**.

Only worry when:

* many `http-nio-*` threads WAIT **inside your app stacks** (Future.get / locks / downstream calls)
* while clients are waiting and latency is rising

---

## 13) Mitigation playbook (safe + reversible actions)

### 13.1 Immediate mitigation

* Rate limit the expensive endpoint.
* Temporarily disable feature / route.
* Shed load: return 429/503 quickly rather than queue forever.
* Reduce concurrency to stop retry storm.

### 13.2 “Fail fast” is better than “hang”

* Timeouts prevent thread retention.
* Circuit breakers prevent hopeless waiting.

### 13.3 Increase capacity (carefully)

* Adding threads can worsen contention and memory.
* Scaling out helps only if downstream can handle it and work completes.

---

## 14) Prevention rules (design + code)

### 14.1 Don’t block request threads

Avoid in controllers:

* `Future.get()`
* long blocking I/O
* `Thread.sleep()`

Use async patterns (with timeouts) or return job IDs.

### 14.2 Bound concurrency (bulkhead)

* semaphore / limited permits
* bounded queues
* per-endpoint caps

Goal: **protect the service** even when a single endpoint misbehaves.

### 14.3 Timeouts (server should timeout before client)

Set server timeouts slightly **shorter** than clients / LB:

* prevents broken pipes
* reduces wasted work

### 14.4 Cancel work when client disconnects

If client aborts:

* stop expensive executor tasks
* free resources
* prevent wasted CPU/thread usage

### 14.5 Name your thread pools

Default `pool-N-thread-M` is usable but not ideal.
Use a custom thread name prefix so JMC/thread dumps are instantly readable.

---

## 15) Observability: what to monitor (beyond CPU/memory)

Must-have signals:

* Request latency (p95/p99)
* Active Tomcat threads / max threads
* Executor active count, queue size, rejection count
* Error rate (but don’t rely on it alone)
* Timeouts (client + server)

Optional but powerful:

* JFR continuous sampling in prod (low overhead)
* Thread state trends

---

## 16) Incident response template (copy/paste)

**Symptoms**:

* p95/p99 latency spike
* increased timeouts (504/502), broken pipes, client aborts
* CPU not necessarily high

**Actions**:

1. Capture thread dump(s) with `jcmd`.
2. Identify whether request threads are blocked in app code.
3. Identify which executor/lock/downstream call is the bottleneck.
4. Mitigate: shed load, cap concurrency, fail fast.
5. Verify recovery: latency normalizes, blocked stacks disappear.
6. Root cause: remove blocking from request path, add bulkheads and timeouts, improve metrics.

---

## 17) One-liners worth memorizing (for on-call & interviews)

* “Thread exhaustion is about availability, not count.”
* “WAITING is healthy unless it blocks progress.”
* “Broken pipe means the client left first.”
* “Executor saturation alone doesn’t imply outage—outage happens when request threads block under concurrent load.”
* “Latency causes errors, not the other way around.”

---

## 18) Quick command cheat sheet

```bash
# list JVMs
jcmd

# thread dump
jcmd <PID> Thread.print > dump.txt

# state summary
jcmd <PID> Thread.print | grep "Thread.State" | sort | uniq -c

# JFR start/dump/stop
jcmd <PID> JFR.start name=threads settings=profile
jcmd <PID> JFR.dump name=threads filename=threads.jfr
jcmd <PID> JFR.stop name=threads

# parallel POST load (zsh-safe)
for i in {1..10}; do
  curl -X POST "http://localhost:8080/fail/threads/block?tasks=10&blockMs=20000" &
done
wait
```

---

## Final rule

If a request thread waits on anything slower than itself, you’ve built a failure amplifier. Design accordingly.

```
```
