# Fraud Detection Agent

## Overview

This service implements a fraud detection API using Exact Vector Similarity (k-NN) via a Compact Vantage-Point Tree (VP-Tree).

- **Target**: p99 latency ≤ 1ms
- **Memory Limit**: 160MB (Total Container)
- **Dataset**: 3M vectors (downsampled to ~1.1M for 160MB fit)

## Tech Stack

- Java 25 + Spring Boot 3.4 (Virtual Threads enabled)
- Vector API (`jdk.incubator.vector`): SIMD-accelerated distance calculations
- VP-Tree: Exact search with $O(\log N)$ average complexity
- Zero-Allocation Hot Path: ThreadLocal buffers for all request-scoped arrays

## Architecture Principles

### 1. Zero Runtime I/O & Allocation

- All data is pre-processed into flat primitive arrays at startup
- No new keywords in `evaluate()` or `search()`
- Manual String Parsing: Substring-based integer parsing replaces `Instant.parse()` to eliminate regex/ISO overhead

### 2. VP-Tree with Aggressive Pruning

Instead of Brute Force, we use a Compact VP-Tree stored in flat arrays:
- Arrays used: `leftChild[]`, `rightChild[]`, `thresholds[]`, and `vpIndices[]`
- Pruning Rule: A branch is only visited if $|dist(query, VP) - threshold| \le dist(query, K^{th} \text{ neighbor})$. This allows the engine to skip up to 95% of the dataset.

### 3. Data Alignment (The "SIMD 16" Rule)

- The Vector API performs best on power-of-2 boundaries
- Padding: 14-dimension vectors are padded to 16 dimensions with zeros
- This allows a 256-bit SIMD register (AVX2) to process a full distance calculation in exactly two cycles, avoiding the "tail loop" penalty

## Memory Budget (160MB Limit)

| Component | Estimated Size (1.1M records) | Notes |
|-----------|------------------------------|-------|
| short[16] Vectors | ~35.2 MB | 1.1M * 16 * 2 bytes |
| byte[] Labels | ~1.1 MB | 1 byte per record |
| VPTree Structure | ~22.0 MB | 4 x int[] + 1 x float[] |
| JVM Heap (Xmx120m) | 120.0 MB | Shared data + buffers |
| **Total Container Usage** | **~155 MB** | Safe for 160MB Limit |

## Performance Optimizations

### Feature Weighting (Precision Fix)

To resolve "Zero True Positives," we apply importance weights during vectorization:
- MCC Risk: $1.5\times$ multiplier
- Known Merchant: $2.0\times$ multiplier
- Missing Data: Replaced with 0 (neutral) or the feature mean instead of -1 to prevent "Distance Explosion"

### JVM Tuning (Mac Mini Late 2014)

```
-Xms120m -Xmx120m
-XX:+UseG1GC
-XX:MaxGCPauseMillis=1
-XX:+AlwaysPreTouch
--add-modules=jdk.incubator.vector
```

## Core Pipeline

1. **Request**: Receive JSON payload
2. **Vectorize**: Extract features via substring, normalize, and fill `ThreadLocal<short[16]>`
3. **Search**: `CompactVPTree.search()` finds 5 nearest neighbors using pruned recursive descent
4. **Score**: Distance-weighted probability calculation
5. **Decision**: Score $\ge 0.45 \rightarrow$ FRAUD

## What NOT to Do

- ❌ No `java.time`: Use manual substring parsing for ISO dates
- ❌ No `FloatVector.fromArray` on 14 elements: Always use padded size (16)
- ❌ No `Stream.parallel()`: Single-threaded search per request is more predictable for p99
- ❌ No `Double`: Use float for thresholds and short for vectors to minimize memory footprint

## Implementation Strategy

1. **Initialize**: Binary load `references.bin` → Build CompactVPTree
2. **Warmup**: Execute 5,000 dummy queries in `@PostConstruct` to trigger C2 JIT
3. **Run**: Zero-allocation scoring loop on Virtual Threads

---

# Fraud Detection Specification

This specification defines how an agent should perform fraud detection using vector similarity (KNN).

**The system:**
- Converts a transaction into a 14-dimensional vector
- Searches the reference dataset
- Computes a fraud score
- Returns an approval decision

## Core Concept

- Each transaction → vector of 14 normalized values
- Similar transactions → close in vector space
- Fraud detection → based on nearest neighbors

## Decision Rule

```
fraud_score = (# fraud neighbors) / K

approved = fraud_score < 0.6
```

- **K = 5** (fixed)
- **Threshold = 0.6** (fixed)

## Processing Pipeline

### Step 1 — Receive Payload

Input structure:

```json
{
  "id": "string",
  "transaction": {
    "amount": number,
    "installments": number,
    "requested_at": "ISO-8601"
  },
  "customer": {
    "avg_amount": number,
    "tx_count_24h": number,
    "known_merchants": ["string"]
  },
  "merchant": {
    "id": "string",
    "mcc": "string",
    "avg_amount": number
  },
  "terminal": {
    "is_online": boolean,
    "card_present": boolean,
    "km_from_home": number
  },
  "last_transaction": {
    "km_from_current": number,
    "requested_at": "ISO-8601"
  } | null
}
```

### Step 2 — Vectorization (14 Dimensions)

Transform payload into vector:

| idx | feature | formula |
|-----|---------|---------|
| 0 | amount | clamp(amount / max_amount) |
| 1 | installments | clamp(installments / max_installments) |
| 2 | amount_vs_avg | clamp((amount / avg_amount) / amount_vs_avg_ratio) |
| 3 | hour | hour(requested_at) / 23 |
| 4 | day_of_week | day_of_week / 6 |
| 5 | minutes_since_last | clamp(minutes / max_minutes) or -1 |
| 6 | km_from_last | clamp(km / max_km) or -1 |
| 7 | km_from_home | clamp(km / max_km) |
| 8 | tx_count_24h | clamp(count / max_tx_count_24h) |
| 9 | is_online | 1 or 0 |
| 10 | card_present | 1 or 0 |
| 11 | unknown_merchant | 1 if unknown else 0 |
| 12 | mcc_risk | lookup or default 0.5 |
| 13 | merchant_avg | clamp(avg / max_merchant_avg_amount) |

#### Special Case — Missing History

If `"last_transaction": null`, then:
- `dim[5] = -1`
- `dim[6] = -1`

> ⚠️ **Do NOT normalize or replace -1**

### Normalization

#### Clamp Function

```
clamp(x):
  if x < 0 → 0
  if x > 1 → 1
  else → x
```

#### Constants (`normalization.json`)

```json
{
  "max_amount": 10000,
  "max_installments": 12,
  "amount_vs_avg_ratio": 10,
  "max_minutes": 1440,
  "max_km": 1000,
  "max_tx_count_24h": 20,
  "max_merchant_avg_amount": 10000
}
```

#### MCC Risk (`mcc_risk.json`)

- Lookup: `mcc_risk[merchant.mcc]`
- Default: 0.5

### Step 3 — Vector Search

#### Distance Function (Euclidean)

$$dist(q,r) = \sum_{i}(q_i - r_i)^2$$

#### Search Strategy

| Option | Description | Complexity |
|--------|-------------|------------|
| **Option A** — Exact (Brute Force) | for each reference: compute distance, sort ascending, take top K | O(N * D) |
| **Option B** — Indexed / Tree-Based | KD-Tree, VP-Tree, Ball Tree, Cover Tree | varies |
| **Option C** — ANN (Approximate) | HNSW → O(log N), IVF → O(√N), LSH → O(N^ρ) | varies |

### Step 4 — Select Neighbors

K = 5 nearest vectors

Each neighbor has:

```json
{
  "vector": [...],
  "label": "fraud" | "legit"
}
```

### Step 5 — Compute Score

```
fraud_score = (# fraud labels) / 5
```

### Step 6 — Decision

```
approved = fraud_score < 0.6
```

### Step 7 — Response

```json
{
  "approved": boolean,
  "fraud_score": number
}
```

## Dataset

- `references.json.gz`
- ~3,000,000 vectors
- 14 dimensions each
- labeled: "fraud" or "legit"

## Important Rules

- Do NOT modify dataset values
- Do NOT filter -1
- Data can be:
  - decompressed
  - indexed
  - preprocessed

## Key Properties

- **Non-Parametric Model**
- No training phase
- All knowledge = dataset
- Decision = runtime similarity

## Behavior Insight

The system:
- Does NOT "understand fraud"
- Only compares patterns

## Summary

```
input → normalize → vector → nearest neighbors → vote → decision
```