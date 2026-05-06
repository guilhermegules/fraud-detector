package com.rinha.frauddetector.application;

import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.util.Arrays;

public class BruteForceKNNSearch {

  private static final VectorSpecies<Short> SPECIES = ShortVector.SPECIES_PREFERRED;
  private static final int VECTOR_SIZE = 14;
  private static final int K = 5;

  private final short[] vectors;
  private final boolean[] labels;
  private final int count;

  public BruteForceKNNSearch(short[] vectors, boolean[] labels, int count) {
    this.vectors = vectors;
    this.labels = labels;
    this.count = count;
  }

  public Result search(short[] query) {
    int[] bestDist = new int[K];
    boolean[] bestLabel = new boolean[K];
    Arrays.fill(bestDist, Integer.MAX_VALUE);

    for (int i = 0; i < count; i++) {
      int base = i * VECTOR_SIZE;
      int dist = distanceSquared(query, base);
      insertIfCloser(bestDist, bestLabel, dist, labels[i]);
    }

    int fraudVotes = 0;
    for (int i = 0; i < K; i++) {
      if (bestLabel[i]) fraudVotes++;
    }

    float score = (float) fraudVotes / K;
    boolean approved = score < 0.5f;
    return new Result(approved, score);
  }

  private int distanceSquared(short[] query, int offset) {
    int i = 0;
    int sum = 0;

    for (; i + SPECIES.length() <= VECTOR_SIZE; i += SPECIES.length()) {
      var va = ShortVector.fromArray(SPECIES, query, i);
      var vb = ShortVector.fromArray(SPECIES, vectors, offset + i);
      var diff = va.sub(vb);
      var squared = diff.mul(diff);
      sum += squared.reduceLanes(VectorOperators.ADD);
    }

    for (; i < VECTOR_SIZE; i++) {
      int d = query[i] - vectors[offset + i];
      sum += d * d;
    }

    return sum;
  }

  private static void insertIfCloser(int[] bestDist, boolean[] bestLabel, int dist, boolean label) {
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

  public record Result(boolean approved, float score) {}
}
