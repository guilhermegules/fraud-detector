package com.rinha.frauddetector.application;

import com.rinha.frauddetector.adapter.engine.VPTree;
import com.rinha.frauddetector.adapter.loader.ReferenceLoader;
import com.rinha.frauddetector.domain.FraudDetectionService;
import com.rinha.frauddetector.domain.FraudScore;
import com.rinha.frauddetector.domain.TransactionVector;
import com.rinha.frauddetector.dto.FraudRequest;

import jakarta.annotation.PostConstruct;

import java.io.IOException;

import static com.rinha.frauddetector.adapter.loader.ReferenceLoader.BUCKET_COUNT;

public class KnnFraudDetectionService implements FraudDetectionService {

  private VPTree[] trees;
  private final ReferenceLoader referenceLoader;
  private static final int K = 5;

  public KnnFraudDetectionService(ReferenceLoader referenceLoader) {
    this.referenceLoader = referenceLoader;
  }

  @PostConstruct
  public void initialize() throws IOException {
    var ref = referenceLoader.loadFraudReference();

    trees = new VPTree[BUCKET_COUNT];

    for (int b = 0; b < BUCKET_COUNT; b++) {
      int start = ref.bucketStarts()[b];
      int end = ref.bucketStarts()[b + 1];

      int size = end - start;
      if (size == 0) continue;

      short[] vectors = new short[size * 14];
      boolean[] labels = new boolean[size];

      System.arraycopy(ref.vectors(), start * 14, vectors, 0, size * 14);
      System.arraycopy(ref.labels(), start, labels, 0, size);

      trees[b] = new VPTree(vectors, labels, 14);
    }
  }

  @Override
  public FraudScore evaluate(FraudRequest request) {
    if (trees == null) {
      throw new IllegalStateException("Dataset not loaded");
    }

    short[] vector = TransactionVector.toArray(
        request,
        referenceLoader.getNormalizationConstants(),
        referenceLoader.getMccRiskMap()
    );

    int bucket = computeBucket(vector);

    VPTree tree = trees[bucket];

    if (tree == null) {
      return FraudScore.SAFE;
    }

    var neighbors = tree.search(vector, K);

    int fraudCount = 0;
    for (var n : neighbors) {
      if (n.label()) fraudCount++;
    }

    float score = (float) fraudCount / K;

    return FraudScore.fromScore(score);
  }

  private int computeBucket(short[] v) {
    int binary = 0;

    if (v[9] > 5000) binary |= 1;
    if (v[10] > 5000) binary |= 2;
    if (v[11] > 5000) binary |= 4;

    int hour = Math.round((v[3] * 23f) / 10000f);
    hour = Math.max(0, Math.min(hour, 23));

    int day = Math.round((v[4] * 6f) / 10000f);
    day = Math.max(0, Math.min(day, 6));

    int tx = v[8] / 2500;
    tx = Math.max(0, Math.min(tx, 3));

    return (((binary * 24) + hour) * 7 + day) * 4 + tx;
  }
}
