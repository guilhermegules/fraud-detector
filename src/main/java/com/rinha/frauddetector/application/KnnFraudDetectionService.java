package com.rinha.frauddetector.application;

import com.rinha.frauddetector.domain.FraudDetectionService;
import com.rinha.frauddetector.domain.FraudScore;
import com.rinha.frauddetector.domain.TransactionVector;
import com.rinha.frauddetector.adapter.engine.VPTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KnnFraudDetectionService implements FraudDetectionService {

  private static final int K = 5;

  private VPTree<TransactionVector> vpTree;
  private boolean[] labels;
  private TransactionVector[] vectors;

  @Override
  public void loadDataset(TransactionVector[] vectors, boolean[] labels) {
    if (vectors == null || labels == null || vectors.length == 0) {
      this.vpTree = null;
      this.labels = new boolean[0];
      this.vectors = new TransactionVector[0];
      return;
    }

    this.vectors = vectors;
    this.labels = labels;

    List<TransactionVector> items = new ArrayList<>(vectors.length);
    Collections.addAll(items, vectors);

    this.vpTree = new VPTree<>(items, TransactionVector::distanceTo);
  }

  @Override
  public FraudScore evaluate(TransactionVector vector) {
    if (vector == null || vpTree == null) {
      return FraudScore.SAFE;
    }

    try {
      List<VPTree.Neighbor<TransactionVector>> neighbors = vpTree.search(vector::distanceTo, K);

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

      double score = (double) fraudVotes / neighbors.size();
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
