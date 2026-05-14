#!/usr/bin/env python3
"""
Convert fraud reference dataset from JSON to binary format.

The binary format is loaded directly by ReferenceLoader.java at startup,
eliminating JSON parsing overhead. The format is:

  [int] signature  (0x52524546 = "RREF")
  [int] version    (2)
  [int] dim        (16)
  [int] count      (number of vectors)
  [short] vectors  (count * 16 little-endian shorts, zero-padded to 16 dims)
  [byte] labels    (count bytes: 1 = fraud, 0 = legit)

Usage:
  python3 precompute.py                              # uses all data
  python3 precompute.py --samples 50000               # stratified 50k samples
  python3 precompute.py --data-dir ../external-data   # custom data path
"""
import argparse
import gzip
import json
import random
import struct
from pathlib import Path

FILE_SIGNATURE = 0x52524546  # "RREF"
VERSION = 2
DIM = 16
SCALE = 10000


def quantize(v: float) -> int:
    q = round(v * SCALE)
    return max(-32768, min(32767, q))


def stratified_sample(vectors, n, rng):
    if n >= len(vectors):
        return vectors
    indices = list(range(len(vectors)))
    rng.shuffle(indices)
    return [vectors[i] for i in indices[:n]]


def parse_args():
    parser = argparse.ArgumentParser(
        description="Convert fraud reference dataset to binary format for the Java fraud detector.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "Examples:\n"
            "  python3 precompute.py\n"
            "  python3 precompute.py --samples 50000\n"
            "  python3 precompute.py --samples 100000 --data-dir ../external-data\n"
        ),
    )
    parser.add_argument(
        "--data-dir",
        type=str,
        default=None,
        help="Path to directory containing references.json.gz (default: ../external-data relative to script)",
    )
    parser.add_argument(
        "--samples", "-k",
        type=int,
        default=0,
        help="Number of stratified samples (0 = use all data)",
    )
    parser.add_argument(
        "--output",
        type=str,
        default=None,
        help="Output path for references.bin (default: ../src/main/resources/references.bin)",
    )
    return parser.parse_args()


def main():
    args = parse_args()
    script_dir = Path(__file__).resolve().parent
    data_dir = Path(args.data_dir) if args.data_dir else script_dir.parent / "external-data"
    output_path = Path(args.output) if args.output else script_dir.parent / "src" / "main" / "resources" / "references.bin"
    k = args.samples

    data_file = data_dir / "references.json.gz"
    print(f"[precompute] Loading references from {data_file}")

    if not data_file.exists():
        print(f"[precompute] ERROR: {data_file} not found.", file=sys.stderr)
        print(f"[precompute] Expected at '{data_file}'. Generate or download the dataset first.", file=sys.stderr)
        sys.exit(1)

    with gzip.open(data_file, "rt") as f:
        data = json.load(f)

    print(f"[precompute] Loaded {len(data)} references")

    fraud = [obj["vector"] for obj in data if obj["label"] == "fraud"]
    legit = [obj["vector"] for obj in data if obj["label"] == "legit"]
    print(f"[precompute] Fraud: {len(fraud)}, Legit: {len(legit)}")

    rng = random.Random(42)

    if k > 0:
        fraud_ratio = len(fraud) / len(data)
        fraud_k = max(1, round(k * fraud_ratio))
        legit_k = k - fraud_k
        print(f"[precompute] Sampling: {fraud_k} fraud + {legit_k} legit = {k} total")
        fraud_centroids = stratified_sample(fraud, fraud_k, rng)
        legit_centroids = stratified_sample(legit, legit_k, rng)
    else:
        print("[precompute] Using all references")
        fraud_centroids = fraud
        legit_centroids = legit

    total = len(fraud_centroids) + len(legit_centroids)

    output_path.parent.mkdir(parents=True, exist_ok=True)
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
    print(f"[precompute] Written {total} references to {output_path} ({file_size} bytes)")


if __name__ == "__main__":
    import sys
    main()
