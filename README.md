# Fraud Detector

High-performance fraud detection service using k-NN with VP-Tree search and SIMD vectorization. Built for the Rinha de Backend competition — zero Spring, zero frameworks, pure Java 25.

## Prerequisites

- Java 25 or higher
- Docker & Docker Compose (for containerized deployment)

## Quick Start

```bash
# 1. Precompute binary reference data (required once)
python3 scripts/precompute.py

# 2. Build the JAR
./gradlew jar

# 3. Run
java --add-modules=jdk.incubator.vector -jar build/libs/frauddetector-0.0.1.jar
```

## Precomputing the Dataset

The fraud detector loads reference vectors from a binary file at startup. Convert the raw JSON dataset:

```bash
# Use all ~3M reference vectors
python3 scripts/precompute.py

# Stratified sample for faster evaluation (e.g. 100k samples)
python3 scripts/precompute.py --samples 100000

# Custom paths
python3 scripts/precompute.py --data-dir ./external-data --output ./src/main/resources/references.bin
```

The binary format maps directly to Java `short[]` and `byte[]` arrays, eliminating JSON parsing overhead at startup. Output goes to `src/main/resources/references.bin` by default.

### Dataset Generation

The full dataset is not included in this repository. Generate it using the tools in `reference/tools/Preprocessor/` (C# K-means clustering).

## Building

### Standalone JAR

```bash
./gradlew jar
```

Output: `build/libs/frauddetector-0.0.1.jar`

### Docker

```bash
# Build image
docker build --platform linux/amd64 -t fraud-detector-rinha containerization/

# Or use Docker Compose
docker compose up --build
```

## Running

### Direct (no container)

```bash
java \
  --add-modules=jdk.incubator.vector \
  -Xms90m -Xmx120m \
  -XX:+UseG1GC -XX:MaxGCPauseMillis=1 \
  -XX:+AlwaysPreTouch -XX:CompileThreshold=1000 \
  -XX:MaxRAM=160m -XX:ReservedCodeCacheSize=64m \
  -jar build/libs/frauddetector-0.0.1.jar
```

The server listens on port 8080 (configurable via `SERVER_PORT` env var).

### Docker Compose (Production)

```bash
cd containerization && docker compose up --build
```

Starts:
- 2 API instances (api1, api2), 160MB RAM each, 0.45 CPU
- HAProxy load balancer on port 9999

### Docker Compose (Development)

```bash
docker compose -f containerization/docker-compose.dev.yml up --build
```

Single API instance on port 8080.

### Build & Publish

```bash
./containerization/build-publish.sh
```

Builds the Docker image and pushes to Docker Hub with a timestamp tag and `latest` tag. Edit the script to set your own image name.

## API

### `GET /ready`

Health check. Returns `{"status":"Ready"}` when the service is initialized.

### `POST /fraud-score`

Evaluates a transaction for fraud risk.

**Request body:**
```json
{
  "id": "tx-1329056812",
  "transaction": { "amount": 384.88, "installments": 3, "requested_at": "2026-03-11T20:23:35Z" },
  "customer": { "avg_amount": 769.76, "tx_count_24h": 3, "known_merchants": ["MERC-009"] },
  "merchant": { "id": "MERC-001", "mcc": "5912", "avg_amount": 298.95 },
  "terminal": { "is_online": false, "card_present": true, "km_from_home": 13.7 },
  "last_transaction": { "requested_at": "2026-03-11T14:58:35Z", "km_from_current": 18.9 }
}
```

**Response:** `{"approved": true, "fraud_score": 0.0}`

## Testing

```bash
./gradlew test
```

Key characteristics:
- **No Spring, no frameworks** — pure JDK 25
- **Zero-dependency JSON** — hand-written streaming parser
- **SIMD k-NN search** — Java Vector API (`ShortVector.SPECIES_256`)
- **VP-Tree** — exact nearest neighbor search with median partitioning
- **Virtual threads** — JDK HttpServer with `newVirtualThreadPerTaskExecutor()`
- **Zero-allocation hot path** — `ThreadLocal` buffer pools for vectors and neighbor heaps
