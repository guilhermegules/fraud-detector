# AGENTS.md (Java 25 / Spring — High Performance Edition)

## Overview

This service implements a **fraud detection API** using **vector similarity (k-NN)** over a dataset of 3M vectors.

Primary goals:

* **p99 latency ≤ 1ms**
* **high throughput**
* **zero runtime I/O**
* **predictable performance under load**
* **fit within 160MB container limit**

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
├── domain
│   ├── FraudDetectionService.java
│   ├── FraudScore.java
│   ├── TransactionVector.java
│   ├── NormalizationConstants.java
│   └── FraudReference.java
├── application
│   ├── KnnFraudDetectionService.java
│   ├── ReferenceAccumulator.java
│   └── BruteForceKNNSearch.java
├── adapter
│   ├── loader
│   │   ├── ReferenceLoader.java
│   │   ├── ReferenceItem.java
│   │   └── ReferenceAccumulator.java
│   └── engine
│       ├── VPTree.java
│       └── Distance.java
└── config
    └── FraudDetectionConfig.java
```

### Layers

* **Input Layer (controller)**: Handles HTTP requests, validates input, returns responses
* **Application Layer (application)**: Orchestrates use cases, contains business logic
* **Domain Layer (domain)**: Core business models and rules
* **Adapter Layer (adapter)**: Loaders and engines (pluggable implementations)
* **Output Layer**: Not needed (no database, everything in memory)

### Dependencies

* Controllers depend on Domain interfaces
* Application services depend on Domain and Adapters
* Adapters depend on Domain
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

    private final FraudDetectionService service;

    @PostMapping("/fraud-score")
    public FraudResponse score(@RequestBody FraudRequest request) {
        return service.evaluate(request);
    }
}
```

---

## Core Pipeline

```
JSON → Vector (float[14]) → KNN (Brute Force) → Score → Response
```

---

## Memory Layout (Critical for 160MB Limit)

### Flat Arrays (AGENTS.md Requirement)

Per AGENTS.md: Use flat primitive arrays for cache locality and minimal memory:

```java
// In ReferenceAccumulator:
float[] vectors;  // size = N * 14, flat contiguous memory
byte[] labels;    // 1 = fraud, 0 = legit
int size;
```

Access pattern:
```java
int base = i * 14;
float v0 = vectors[base];
float v1 = vectors[base + 1];
// ...
```

### Memory Budget (160MB Container)

| Component | Estimated Size (1.1M records) |
|-----------|-------------------------------|
| `float[14]` flat array | ~60MB (1.1M * 14 * 4 bytes) |
| `byte[]` labels | ~1MB |
| JVM overhead (heap, metaspace) | ~50-60MB |
| Spring + code | ~30-40MB |
| **Total** | **~140-160MB** |

---

## Vectorization (Java 25 Style)

### Guidelines

* Use **primitive arrays (`float[]`)**
* Avoid object allocation in hot path
* Inline normalization logic

### Clamp Utility

```java
static float clamp(float v) {
    return Math.clamp(v, 0f, 1f);
}
```

---

## Dataset Representation

### Precomputation (Binary Format)

**Why needed:** JSON parsing at startup is slow and memory-intensive. The precompute script converts `references.json.gz` to a compact binary format that loads directly into memory structures.

**Running the script:**
```bash
python3 precompute.py [SAMPLE_RATE]
# Example: python3 precompute.py 0.005  # 0.5% sample rate
```

**Output files:**
- `references.bin`: Binary file with header (record count) + flat float array (N×14) + byte labels
- `normalization.bin`: Serialized normalization constants (if applicable)

**Binary format structure (`references.bin`):**
```
[4 bytes: record count (uint32, little-endian)]
[N×14×4 bytes: float array (little-endian)]
[N bytes: labels (0=legit, 1=fraud)]
```

**Benefits:**
- Eliminates JSON parsing overhead at startup
- Direct memory-mapped loading into `float[]` and `byte[]`
- Reduced container startup time (critical for fast scaling)
- Smaller file size than compressed JSON

### Loading Process

1. **Precompute step** (`precompute.py`):
   * Reads `references.json.gz` and samples legit transactions
   * Outputs `references.bin` with binary format
   * Configurable `SAMPLE_RATE` (default: 0.005 = 0.5%)

2. **Binary load** (`ReferenceLoader.loadReferences`):
   * Reads `references.bin` directly into memory structures
   * No JSON parsing, no streaming overhead
   * Populates flat `float[]` vectors and `byte[]` labels

3. **Search Structure** (`BruteForceKNNSearch`):
   * Brute force k-NN with SIMD (Vector API)
   * No VP-tree overhead (following AGENTS.md "start simple" advice)

---

## Distance Computation

### DO NOT use sqrt

Compare squared distances only.

### SIMD-Accelerated Distance (Vector API — Java 25)

```java
var SPECIES = FloatVector.SPECIES_PREFERRED;

float distanceSquared(float[] query, int offset) {
    int i = 0;
    var sum = FloatVector.zero(SPECIES);

    for (; i + SPECIES.length() <= 14; i += SPECIES.length()) {
        var va = FloatVector.fromArray(SPECIES, query, i);
        var vb = FloatVector.fromArray(SPECIES, vectors, offset + i);
        var diff = va.sub(vb);
        sum = sum.add(diff.mul(diff));
    }

    float result = sum.reduceLanes(VectorOperators.ADD);

    for (; i < 14; i++) {
        float d = query[i] - vectors[offset + i];
        result += d * d;
    }

    return result;
}
```

---

## Nearest Neighbors (K=5)

### Fast Selection

Use fixed-size structure (no heap allocation):

```java
float[] bestDist = new float[5];
byte[] bestLabel = new byte[5];
```

Insertion with manual shift (avoids sorting):
```java
private static void insertIfCloser(float[] bestDist, byte[] bestLabel, float dist, byte label) {
    for (int j = 0; j < K; j++) {
        if (dist < bestDist[j]) {
            for (int k = K - 1; k > j; k--) {
                bestDist[k] = bestDist[k - 1];
                bestLabel[k] = bestLabel[k - 1];
            }
            bestDist[j] = dist;
            bestLabel[j] = label;
            return;
        }
    }
}
```

---

## Parallelism (Java 25)

### Structured Concurrency (Future Use)

```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

    var task1 = scope.fork(() -> searchChunk(0, mid));
    var task2 = scope.fork(() -> searchChunk(mid, N));

    scope.join();

    return merge(task1.get(), task2.get());
}
```

Note: Current implementation uses brute force single-threaded for simplicity. Add parallelism only after measuring.

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

## GC Strategy (160MB Constraint)

* **ZGC** for low latency and predictable pauses
* **AlwaysPreTouch** to fault-in memory at startup

JVM Settings (Dockerfile):
```
-Xms128m
-Xmx128m
-XX:+UseZGC
-XX:+AlwaysPreTouch
--add-modules=jdk.incubator.vector
```

**Why 128MB heap?** Container limit is 160MB. JVM native memory (metaspace, code cache, etc.) needs ~30-40MB, leaving ~120-130MB for heap.

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
| Memory          | ≤ 160MB (container)     |
| Allocation rate | ~0 in hot path          |
| Dataset loaded  | ~1.1M vectors (5% sample) |

---

## Advanced Optimizations

### 1. Data-Oriented Design

* **SoA (Structure of Arrays)** preferred for vector data
* Flat `float[]` with stride-14 access pattern

### 2. Loop Optimization

* SIMD via Vector API (`jdk.incubator.vector`)
* Manual unrolling if needed

### 3. Branch Reduction

* Avoid `if` inside tight loops
* Use conditional moves where possible

### 4. Precomputation

* Pre-normalize vectors at load time
* Cache normalization constants

---

## What NOT to Do

* ❌ No database
* ❌ No per-request allocations (reuse arrays)
* ❌ No streams API in hot path
* ❌ No logging in scoring path
* ❌ No synchronized blocks in critical loops
* ❌ No VPTree/ANN until brute force is proven insufficient

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

## Implementation Strategy (AGENTS.md Aligned)

Following AGENTS.md "Final Advice":

1. ✅ **Brute force + optimized loop** (current: `BruteForceKNNSearch`)
2. ✅ **Measure** (profile before optimizing)
3. ✅ **Add SIMD** (current: Vector API in distance computation)
4. ⏳ **Add parallelism** (only if needed, structured concurrency)
5. ⏳ **Consider ANN** (VP-tree exists in `adapter/engine/` for future use)

---

## Summary

Winning strategy:

* Keep data hot in memory (flat arrays)
* Optimize inner loops aggressively (SIMD)
* Use Java 25 features where they matter
* Avoid complexity until necessary
* **Fit within 160MB container limit**

Focus on:

> **CPU efficiency + memory locality + zero overhead**

---

## Code Formatting (Google Java Style Guide)

### Source File Basics

* Files encoded in **UTF-8**
* Use **2 spaces** for indentation (no tabs)
* File length: max **1000 lines**
* No wildcard imports (`import java.util.*`)
* Import order: same-package, third-party, java, javax

### Braces

* Use **K&R style** (opening brace at end of line)
* Braces required for all control structures (if, else, for, while, etc.)
* Empty blocks: `{}` preferred over `;`

```java
if (condition) {
    doSomething();
} else {
    doOther();
}
```

### Column Limit

* **100 characters** max per line
* Wrap lines at logical points (operators, after commas)
* Indent continuation lines **4 spaces**

### Naming Conventions

| Element | Convention | Example |
|---------|-------------|---------|
| Package | all lowercase, no underscores | `com.rinha.frauddetector` |
| Class | UpperCamelCase | `BruteForceKNNSearch` |
| Method | lowerCamelCase | `evaluateRequest()` |
| Variable | lowerCamelCase | `vectorCount` |
| Constant | UPPER_UNDERSCORE | `VECTOR_SIZE` |
| Type Parameter | Single uppercase | `T`, `E`, `K`, `V` |

### Whitespace

* One blank line between methods
* No trailing whitespace
* Blank lines optional at beginning/end of file
* Space after keywords: `if`, `for`, `while`, `catch`
* Space before opening brace `{`
* Space around operators: `=`, `+`, `==`, `&&`

### Declarations

* One declaration per line
* Variable declarations close to first use
* Array: `float[] vectors` NOT `float vectors[]`

### Programming Practices

* No raw types (use generics)
* Use `@Override` always when applicable
* Caught exceptions: either rethrow or log, not ignore
* Static members: access via class name, not instance

### Javadoc

* Required for public/protected classes and methods
* First sentence ends with period
* No `{@inheritDoc}` abuse
* Missing Javadoc: use `//` comment or nothing

```java
/**
 * Calculates the fraud score based on k-NN algorithm.
 *
 * @param request the fraud request to evaluate
 * @return the calculated fraud score
 */
public FraudScore evaluate(FraudRequest request) { ... }
```

---

## Unit Tests

### Guidelines

* Use JUnit 5
* Test business logic in service layer
* Mock dependencies when needed
* Test coverage for critical paths
* Follow Google Java Style Guide for test code

### Test Structure

```
src/test/java/com/rinha/frauddetector
├── controller
│   ├── HealthControllerTest.java
│   └── FraudControllerTest.java
├── application
│   ├── KnnFraudDetectionServiceTest.java
│   └── BruteForceKNNSearchTest.java
├── domain
│   ├── FraudScoreTest.java
│   └── TransactionVectorTest.java
├── adapter
│   ├── loader
│   │   └── ReferenceLoaderTest.java
│   └── engine
│       └── VPTreeTest.java
└── integration
    └── FraudDetectionIntegrationTest.java
```

### Example Test

```java
@SpringBootTest
class FraudControllerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void shouldReturnFraudScore() throws Exception {
    String requestJson =
        """
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

    mockMvc.perform(
            post("/fraud-score")
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
* No trailing whitespace
* Max 100 characters per line
