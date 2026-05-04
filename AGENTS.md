# AGENTS.md (Java 25 / Spring — High Performance Edition)

## Overview

This service implements a **fraud detection API** using **vector similarity (k-NN)** over a dataset of 3M vectors.

Primary goals:

* **p99 latency ≤ 1ms**
* **high throughput**
* **zero runtime I/O**
* **predictable performance under load**

---

## Tech Stack

* **Java 25**
* **Spring Boot (minimal footprint)**
* **Virtual Threads (Project Loom)**
* **Vector API (SIMD acceleration)**
* Jackson (JSON)
* No database

---

## Architecture Principles

### 1. Everything in Memory

* Load and preprocess dataset at startup
* No disk or network access during requests

### 2. CPU-bound Optimization

* This is a **numerical computation problem**
* Optimize:

    * cache locality
    * branch prediction
    * memory layout

### 3. Minimize Framework Overhead

* Spring only handles HTTP layer
* No AOP, no heavy filters, no reflection-heavy features

---

## Hexagonal Architecture

### Package Structure

```
com.rinha.frauddetector
├── controller
│   ├── HealthController.java
│   └── FraudController.java
├── dto
│   ├── FraudRequest.java
│   ├── FraudResponse.java
│   ├── TransactionDTO.java
│   ├── CustomerDTO.java
│   ├── MerchantDTO.java
│   ├── TerminalDTO.java
│   └── LastTransactionDTO.java
├── service
│   └── FraudService.java
├── domain
│   └── (domain models)
└── application
    └── (use cases)
```

### Layers

* **Input Layer (controller)**: Handles HTTP requests, validates input, returns responses
* **Application Layer (service)**: Orchestrates use cases, contains business logic
* **Domain Layer (domain)**: Core business models and rules
* **Output Layer**: Not needed (no database, everything in memory)

### Dependencies

* Controllers depend on Services
* Services depend on Domain
* Spring annotations only in controllers and configuration

---

## API Layer

### Health Controller

```java
@RestController
public class HealthController {

    @GetMapping("/ready")
    public void ready(HttpServletResponse res) {
        res.setStatus(200);
    }
}
```

### Fraud Controller

```java
@RestController
public class FraudController {

    private final FraudService service;

    @PostMapping("/fraud-score")
    public FraudResponse score(@RequestBody FraudRequest request) {
        return service.evaluate(request);
    }
}
```

---

## Core Pipeline

```
JSON → Vector (float[14]) → KNN → Score → Response
```

---

## Vectorization (Java 25 Style)

### Guidelines

* Use **primitive arrays (`float[]`)**
* Avoid object allocation
* Inline normalization logic

### Clamp Utility

```java
static float clamp(float v) {
    return Math.max(0f, Math.min(1f, v));
}
```

---

## Dataset Representation

### Memory Layout (Critical)

Prefer **flat arrays** for better cache locality:

```java
float[] vectors; // size = N * 14
byte[] labels;   // 1 = fraud, 0 = legit
```

Access:

```java
int base = i * 14;
float v0 = vectors[base];
```

---

## Distance Computation

### DO NOT use sqrt

Compare squared distances only.

---

## SIMD Optimization (Vector API — Java 25)

Use `jdk.incubator.vector` (or stable equivalent if finalized).

### Example

```java
var SPECIES = FloatVector.SPECIES_PREFERRED;

float distance(float[] a, float[] b, int offset) {
    int i = 0;
    var sum = FloatVector.zero(SPECIES);

    for (; i + SPECIES.length() <= 14; i += SPECIES.length()) {
        var va = FloatVector.fromArray(SPECIES, a, i);
        var vb = FloatVector.fromArray(SPECIES, b, offset + i);
        var diff = va.sub(vb);
        sum = sum.add(diff.mul(diff));
    }

    float result = sum.reduceLanes(VectorOperators.ADD);

    for (; i < 14; i++) {
        float d = a[i] - b[offset + i];
        result += d * d;
    }

    return result;
}
```

---

## Nearest Neighbors (K=5)

### Fast Selection

Use fixed-size structure:

```java
float[] bestDist = new float[5];
byte[] bestLabel = new byte[5];
```

* Manual insertion (no heap)
* Avoid sorting entire dataset

---

## Parallelism (Java 25)

### Structured Concurrency (Preferred)

```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

    var task1 = scope.fork(() -> searchChunk(0, mid));
    var task2 = scope.fork(() -> searchChunk(mid, N));

    scope.join();

    return merge(task1.get(), task2.get());
}
```

Benefits:

* Better control than raw threads
* Predictable cancellation
* Cleaner code

---

## Virtual Threads

Enable:

```
spring.threads.virtual.enabled=true
```

Use for:

* HTTP handling
* NOT for CPU-heavy loops (keep those tight and local)

---

## GC Strategy

* Prefer **ZGC or Shenandoah**
* Keep allocation rate near zero in hot path

Example:

```
-XX:+UseZGC
-XX:+AlwaysPreTouch
```

---

## Warmup Strategy

* Run dummy queries at startup
* Trigger JIT compilation before test

---

## Error Handling

Never return HTTP errors.

Fallback:

```java
return new FraudResponse(true, 0.0f);
```

---

## Performance Targets

| Metric          | Target                  |
| --------------- | ----------------------- |
| p99             | ≤ 1ms                   |
| Memory          | Efficient (fit dataset) |
| Allocation rate | ~0 in hot path          |

---

## Advanced Optimizations

### 1. Data-Oriented Design

* Prefer **SoA (Structure of Arrays)** if beneficial

### 2. Loop Optimization

* Unroll loops manually if needed

### 3. Branch Reduction

* Avoid `if` inside tight loops

### 4. Precomputation

* Pre-normalize everything possible

---

## What NOT to Do

* ❌ No database
* ❌ No per-request allocations
* ❌ No streams API in hot path
* ❌ No logging in scoring path
* ❌ No synchronized blocks in critical loops

---

## Key Insight

This problem is:

> **high-performance vector math under latency constraints**

Spring is just transport.

Java 25 gives you:

* SIMD (Vector API)
* Lightweight concurrency
* Better GC

Use them deliberately.

---

## Final Advice

Start simple:

1. Brute force + optimized loop
2. Measure
3. Add SIMD
4. Add parallelism
5. Only then consider ANN

---

## Summary

Winning strategy:

* Keep data hot in memory
* Optimize inner loops aggressively
* Use Java 25 features where they matter
* Avoid complexity until necessary

Focus on:

> **CPU efficiency + memory locality + zero overhead**

---

## Unit Tests

### Guidelines

* Use JUnit 5
* Test business logic in service layer
* Mock dependencies when needed
* Test coverage for critical paths

### Test Structure

```
src/test/java/com/rinha/frauddetector
├── controller
│   ├── HealthControllerTest.java
│   └── FraudControllerTest.java
├── service
│   └── FraudServiceTest.java
└── FrauddetectorApplicationTests.java
```

### Example Test

```java
@SpringBootTest
class FraudControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnFraudScore() throws Exception {
        String requestJson = """
            {
                "id": "tx-123",
                "transaction": {
                    "amount": 100.0,
                    "installments": 1,
                    "requested_at": "2026-03-11T20:23:35Z"
                },
                "customer": {
                    "avg_amount": 500.0,
                    "tx_count_24h": 2,
                    "known_merchants": ["MERC-001"]
                },
                "merchant": {
                    "id": "MERC-001",
                    "mcc": "5912",
                    "avg_amount": 300.0
                },
                "terminal": {
                    "is_online": false,
                    "card_present": true,
                    "km_from_home": 10.0
                },
                "last_transaction": null
            }
            """;

        mockMvc.perform(post("/fraud-score")
                .contentType("application/json")
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").exists())
                .andExpect(jsonPath("$.fraud_score").exists());
    }
}
```

### Rinha Rules for Tests

* No database connections in tests
* Fast execution (p99 ≤ 1ms per test)
* No external service calls
* Use in-memory data for service tests
