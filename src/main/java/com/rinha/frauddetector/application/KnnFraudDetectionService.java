package com.rinha.frauddetector.application;

import com.rinha.frauddetector.adapter.loader.ReferenceLoader;
import com.rinha.frauddetector.domain.FraudDetectionService;
import com.rinha.frauddetector.domain.FraudScore;
import com.rinha.frauddetector.domain.TransactionVector;
import com.rinha.frauddetector.dto.FraudRequest;

import jakarta.annotation.PostConstruct;
import jdk.incubator.vector.*;

import java.io.IOException;
import java.util.Random;

import static com.rinha.frauddetector.adapter.loader.ReferenceLoader.BUCKET_COUNT;

public class KnnFraudDetectionService implements FraudDetectionService {

  private static final VectorSpecies<Short> SPECIES = ShortVector.SPECIES_256;
  private static final VectorSpecies<Integer> INT_SPECIES = IntVector.SPECIES_256;

  private short[] allVectors;
  private boolean[] allLabels;
  private int[] bucketStarts;

  private final ReferenceLoader referenceLoader;
  private static final int K = 5;
  private static final int BUCKET_RANGE = 7;

  private static final ThreadLocal<short[]> VECTOR_BUFFER = ThreadLocal.withInitial(() -> new short[16]);
  private static final ThreadLocal<int[]> BEST_DISTS =
      ThreadLocal.withInitial(() -> new int[K]);
  private static final ThreadLocal<boolean[]> BEST_LABELS =
      ThreadLocal.withInitial(() -> new boolean[K]);

  public KnnFraudDetectionService(ReferenceLoader referenceLoader) {
    this.referenceLoader = referenceLoader;
  }

  @PostConstruct
  public void initialize() throws IOException {
    var ref = referenceLoader.loadFraudReference();

    int size = ref.labels().length;
    allVectors = new short[size * 16];
    allLabels = ref.labels();
    bucketStarts = ref.bucketStarts();

    System.arraycopy(ref.vectors(), 0, allVectors, 0, size * 14);

    warmup();
  }

  @Override
  public FraudScore evaluate(FraudRequest request) {
    if (allVectors == null) {
      throw new IllegalStateException("Dataset not loaded");
    }

    short[] vector = VECTOR_BUFFER.get();
    TransactionVector.toArray(request,
        referenceLoader.getNormalizationConstants(),
        referenceLoader.getMccRiskMap(),
        vector);

    int bucket = computeBucket(vector);

    int[] bestDists = BEST_DISTS.get();
    boolean[] bestLabels = BEST_LABELS.get();
    for (int i = 0; i < K; i++) bestDists[i] = Integer.MAX_VALUE;

    searchBuckets(vector, bucket, bestDists, bestLabels);

    int fraudNeighbors = 0;
    for (int i = 0; i < K; i++) {
      if (bestDists[i] == Integer.MAX_VALUE) break;
      if (bestLabels[i]) fraudNeighbors++;
    }

    return FraudScore.fromFraudCount(fraudNeighbors);
  }

  private void searchBuckets(short[] vector, int bucket, int[] bestDists, boolean[] bestLabels) {
    var qVec = ShortVector.fromArray(SPECIES, vector, 0);
    for (int offset = -BUCKET_RANGE; offset <= BUCKET_RANGE; offset++) {
      int b = bucket + offset;
      if (b < 0 || b >= BUCKET_COUNT) continue;

      int start = bucketStarts[b];
      int end = bucketStarts[b + 1];

      for (int idx = start; idx < end; idx++) {
        int base = idx * 16;
        var rVec = ShortVector.fromArray(SPECIES, allVectors, base);
        var vd = qVec.sub(rVec);
        var vLo = (IntVector) vd.convertShape(VectorOperators.S2I, INT_SPECIES, 0);
        var vHi = (IntVector) vd.convertShape(VectorOperators.S2I, INT_SPECIES, 1);
        int dist = vLo.mul(vLo).reduceLanes(VectorOperators.ADD)
                 + vHi.mul(vHi).reduceLanes(VectorOperators.ADD);

        if (dist >= bestDists[K - 1]) continue;

        int i = K - 2;
        while (i >= 0 && bestDists[i] > dist) {
          bestDists[i + 1] = bestDists[i];
          bestLabels[i + 1] = bestLabels[i];
          i--;
        }
        bestDists[i + 1] = dist;
        bestLabels[i + 1] = allLabels[idx];
      }
    }
  }

  private int computeBucket(short[] v) {
    int binary = 0;
    if (v[9] > 5000) binary |= 1;
    if (v[10] > 5000) binary |= 2;
    if (v[11] > 5000) binary |= 4;

    int hour = Math.round((v[3] * 23f) / 10000f);
    hour = Math.clamp(hour, 0, 23);

    int day = Math.round((v[4] * 6f) / 10000f);
    day = Math.clamp(day, 0, 6);

    int tx = v[8] / 2500;
    tx = Math.clamp(tx, 0, 3);

    return (((binary * 24) + hour) * 7 + day) * 4 + tx;
  }

  private void warmup() {
    var random = new Random(42);
    short[] vector = new short[16];

    int[] bestDists = new int[K];
    boolean[] bestLabels = new boolean[K];

    for (int q = 0; q < 5000; q++) {
      for (int j = 0; j < 16; j++) {
        vector[j] = (short) random.nextInt(10001);
      }
      int bucket = computeBucket(vector);
      searchBuckets(vector, bucket, bestDists, bestLabels);
    }
  }
}
