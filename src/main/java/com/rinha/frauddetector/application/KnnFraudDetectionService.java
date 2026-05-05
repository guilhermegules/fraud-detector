package com.rinha.frauddetector.application;

import com.rinha.frauddetector.adapter.loader.ReferenceLoader;
import com.rinha.frauddetector.domain.FraudDetectionService;
import com.rinha.frauddetector.domain.FraudScore;
import com.rinha.frauddetector.domain.TransactionVector;
import com.rinha.frauddetector.adapter.engine.VPTree;
import com.rinha.frauddetector.dto.FraudRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class KnnFraudDetectionService implements FraudDetectionService {

  private static final int NEAREST_NEIGHBOR = 5;

  private VPTree<TransactionVector> vpTree;
  private boolean[] labels;
  private TransactionVector[] vectors;
  private final ReferenceLoader referenceLoader;

  public KnnFraudDetectionService(ReferenceLoader referenceLoader) {
    this.referenceLoader = referenceLoader;
    try {
      ReferenceAccumulator acc = new ReferenceAccumulator(1024);

      referenceLoader.loadReferences(acc::add);

      TransactionVector[] vectors = Arrays.copyOf(acc.vectors, acc.size);
      boolean[] labels = Arrays.copyOf(acc.labels, acc.size);

      loadDataset(vectors, labels);
    } catch (IOException e) {
      System.out.println(e.getMessage());
      throw new RuntimeException(e);
    }
  }

  @Override
  public void loadDataset(TransactionVector[] vectors, boolean[] labels) {
    this.vectors = vectors;
    this.labels = labels;

    List<TransactionVector> items = new ArrayList<>(vectors.length);
    Collections.addAll(items, vectors);

    this.vpTree = new VPTree<>(items, TransactionVector::distanceTo);
  }

  @Override
  public FraudScore evaluate(FraudRequest request) {
    TransactionVector vector = TransactionVector.fromRequest(
            request,
            referenceLoader.getNormalizationConstants(),
            referenceLoader.getMccRiskMap()
    );

    if (vpTree == null) {
      return FraudScore.SAFE;
    }

    try {
      List<VPTree.Neighbor<TransactionVector>> neighbors = vpTree.search(vector::distanceTo, NEAREST_NEIGHBOR);

      if (neighbors.isEmpty()) {
        return FraudScore.SAFE;
      }

      long fraudVotes =
          neighbors.stream()
              .mapToInt(
                  n -> {
                    int idx = findIndex(n.item());
                    return (idx >= 0 && idx < labels.length && labels[idx]) ? 1 : 0;
                  })
              .sum();

      float score = (float) fraudVotes / neighbors.size();
      return FraudScore.fromScore(score);
    } catch (Exception e) {
      return FraudScore.SAFE;
    }
  }

  @Override
  public boolean isDatasetLoaded() {
    return vpTree != null;
  }

  private int findIndex(TransactionVector target) {
    for (int i = 0; i < vectors.length; i++) {
      if (vectors[i] == target) {
        return i;
      }
    }
    return -1;
  }
}
