package com.rinha.frauddetector.application;

import com.rinha.frauddetector.adapter.engine.VPTree;
import com.rinha.frauddetector.adapter.loader.ReferenceLoader;
import com.rinha.frauddetector.domain.FraudDetectionService;
import com.rinha.frauddetector.domain.FraudScore;
import com.rinha.frauddetector.domain.TransactionVector;
import com.rinha.frauddetector.dto.FraudRequest;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.util.Random;

public class KnnFraudDetectionService implements FraudDetectionService {

  private VPTree tree;
  private final ReferenceLoader referenceLoader;

  private static final ThreadLocal<short[]> VECTOR_BUFFER = ThreadLocal.withInitial(() -> new short[16]);
  private static final ThreadLocal<VPTree.Neighbor[]> HEAP_BUFFER =
      ThreadLocal.withInitial(() -> {
        var h = new VPTree.Neighbor[FraudScore.K];
        for (int i = 0; i < FraudScore.K; i++) h[i] = new VPTree.Neighbor();
        return h;
      });

  public KnnFraudDetectionService(ReferenceLoader referenceLoader) {
    this.referenceLoader = referenceLoader;
  }

  @PostConstruct
  public void initialize() throws IOException {
    var ref = referenceLoader.loadFraudReference();
    tree = new VPTree(ref.vectors(), ref.labels(), 16);
    warmup();
  }

  @Override
  public FraudScore evaluate(FraudRequest request) {
    short[] vector = VECTOR_BUFFER.get();
    TransactionVector.toArray(request,
        referenceLoader.getNormalizationConstants(),
        referenceLoader.getMccRiskMap(),
        vector);

    var heap = HEAP_BUFFER.get();
    tree.search(vector, FraudScore.K, heap);
    int fraudNeighbors = 0;
    for (int i = 0; i < FraudScore.K; i++) {
      if (heap[i].distance() == Integer.MAX_VALUE) break;
      if (heap[i].label()) fraudNeighbors++;
    }

    return FraudScore.fromFraudCount(fraudNeighbors);
  }

  private void warmup() {
    var random = new Random(42);
    var vector = new short[16];
    var heap = new VPTree.Neighbor[FraudScore.K];
    for (int i = 0; i < FraudScore.K; i++) heap[i] = new VPTree.Neighbor();

    for (int q = 0; q < 50000; q++) {
      for (int j = 0; j < 16; j++) {
        vector[j] = (short) random.nextInt(10001);
      }
      tree.search(vector, FraudScore.K, heap);
    }
  }
}
