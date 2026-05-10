#!/usr/bin/env python3
import json
import gzip
import struct
import random
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent

SAMPLE_RATE = float(sys.argv[1]) if len(sys.argv) > 1 else 0.20
RANDOM_SEED = 42

FILE_SIGNATURE = 0x52524546  # "RREF"
VERSION = 2  # 14-dim → 16-dim padded
DIM = 16  # padded from 14 for SIMD alignment
SCALE = 10000

def main():
    random.seed(RANDOM_SEED)

    vectors = []
    labels = []
    fraud_count = 0
    legit_count = 0

    print(f"Loading references with SAMPLE_RATE={SAMPLE_RATE}...")

    with gzip.open(SCRIPT_DIR / "../external-data/references.json.gz", "rt") as f:
        data = json.load(f)

        for obj in data:
            vector = obj["vector"]
            is_fraud = obj["label"] == "fraud"

            # Sample both fraud and legit to maintain balance
            if random.random() > SAMPLE_RATE:
                continue

            for v in vector:
                s = int(v * SCALE)
                s = max(0, min(SCALE, s))
                vectors.append(s)

            # pad to 16 dimensions for SIMD alignment
            for _ in range(len(vector), DIM):
                vectors.append(0)

            labels.append(is_fraud)
            if is_fraud:
                fraud_count += 1
            else:
                legit_count += 1

    count = len(labels)
    print(f"Loaded {count} records ({fraud_count} fraud, {legit_count} legit)")

    with open(SCRIPT_DIR / "../src/main/resources/references.bin", "wb") as f:
        # HEADER (little-endian)
        f.write(struct.pack("<I", FILE_SIGNATURE))
        f.write(struct.pack("<I", VERSION))
        f.write(struct.pack("<I", DIM))
        f.write(struct.pack("<I", count))

        # SHORT VECTORS
        for v in vectors:
            f.write(struct.pack("<h", v))

        # LABELS
        for label in labels:
            f.write(struct.pack("?", label))

    size = 16 + count * DIM * 2 + count
    print(f"Written references.bin ({count} records)")
    print(f"Estimated size: {size} bytes (padded to {DIM} dims)")

if __name__ == "__main__":
    main()