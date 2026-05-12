#!/usr/bin/env python3
import json
import gzip
import struct
import random
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent

resources_path = Path(sys.argv[1]) if len(sys.argv) > 1 else SCRIPT_DIR.parent / "external-data"
k = int(sys.argv[2]) if len(sys.argv) > 2 else 0

FILE_SIGNATURE = 0x52524546  # "RREF"
VERSION = 2
DIM = 16
SCALE = 8192


def quantize(v: float) -> int:
    q = round(v * SCALE)
    if q > 32767:
        q = 32767
    elif q < -32768:
        q = -32768
    return q


def stratified_sample(vectors, n, rng):
    if n >= len(vectors):
        return vectors
    indices = list(range(len(vectors)))
    rng.shuffle(indices)
    return [vectors[i] for i in indices[:n]]


def main():
    print(f"Loading references from {resources_path}...")

    with gzip.open(resources_path / "references.json.gz", "rt") as f:
        data = json.load(f)

    print(f"Loaded {len(data)} references.")

    fraud = [obj["vector"] for obj in data if obj["label"] == "fraud"]
    legit = [obj["vector"] for obj in data if obj["label"] == "legit"]
    print(f"Fraud: {len(fraud)}, Legit: {len(legit)}")

    rng = random.Random(42)

    if k > 0:
        fraud_ratio = len(fraud) / len(data)
        fraud_k = max(1, round(k * fraud_ratio))
        legit_k = k - fraud_k
        print(f"Sampling: {fraud_k} fraud + {legit_k} legit = {k} total")
        fraud_centroids = stratified_sample(fraud, fraud_k, rng)
        legit_centroids = stratified_sample(legit, legit_k, rng)
    else:
        print("Converting all references to binary.")
        fraud_centroids = fraud
        legit_centroids = legit

    output_path = SCRIPT_DIR.parent / "src" / "main" / "resources" / "references.bin"
    total = len(fraud_centroids) + len(legit_centroids)

    with open(output_path, "wb") as f:
        f.write(struct.pack("<i", FILE_SIGNATURE))
        f.write(struct.pack("<i", VERSION))
        f.write(struct.pack("<i", DIM))
        f.write(struct.pack("<i", total))

        for vec in fraud_centroids + legit_centroids:
            for i in range(14):
                f.write(struct.pack("<h", quantize(vec[i])))
            f.write(struct.pack("<h", 0))
            f.write(struct.pack("<h", 0))

        for _ in fraud_centroids:
            f.write(struct.pack("B", 1))
        for _ in legit_centroids:
            f.write(struct.pack("B", 0))

    file_size = output_path.stat().st_size
    print(f"Written {total} references to {output_path} ({file_size} bytes)")


if __name__ == "__main__":
    main()
