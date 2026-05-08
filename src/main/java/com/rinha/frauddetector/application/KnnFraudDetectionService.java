package com.rinha.frauddetector.application;

import com.rinha.frauddetector.adapter.engine.VPTree;
import com.rinha.frauddetector.adapter.loader.ReferenceLoader;
import com.rinha.frauddetector.domain.FraudDetectionService;
import com.rinha.frauddetector.domain.FraudScore;
import com.rinha.frauddetector.domain.TransactionVector;
import com.rinha.frauddetector.dto.FraudRequest;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

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

    IntStream.range(0, BUCKET_COUNT).parallel().forEach(b -> {
      int start = ref.bucketStarts()[b];
      int end = ref.bucketStarts()[b + 1];

      int size = end - start;
      if (size == 0) return;

      short[] vectors = new short[size * 16];
      boolean[] labels = new boolean[size];

      System.arraycopy(ref.vectors(), start * 14, vectors, 0, size * 14);
      System.arraycopy(ref.labels(), start, labels, 0, size);

      trees[b] = new VPTree(vectors, labels, 16);
    });
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

    List<VPTree.Neighbor> all = new ArrayList<>();

    for (int b : neighborBuckets(bucket)) {
      VPTree t = trees[b];
      if (t != null) {
        all.addAll(t.search(vector, K));
      }
    }

    // keep top K globally
    all.sort(Comparator.comparingDouble(VPTree.Neighbor::distance));

    List<VPTree.Neighbor> neighbors = all.subList(0, Math.min(K, all.size()));

    int fraudCount = 0;
    for (var n : neighbors) {
      if (n.label()) {
        fraudCount++;
      }
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
    hour = Math.clamp(hour, 0, 23);

    int day = Math.round((v[4] * 6f) / 10000f);
    day = Math.clamp(day, 0, 6);

    int tx = v[8] / 2500;
    tx = Math.clamp(tx, 0, 3);

    return (((binary * 24) + hour) * 7 + day) * 4 + tx;
  }

  private int[] neighborBuckets(int b) {
    return new int[] {
            b,
            Math.max(0, b - 1),
            Math.min(BUCKET_COUNT - 1, b + 1)
    };
  }
}
