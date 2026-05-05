package com.rinha.frauddetector.application;

import com.rinha.frauddetector.adapter.loader.ReferenceItem;
import com.rinha.frauddetector.domain.TransactionVector;

import java.util.Arrays;

public class ReferenceAccumulator {

  TransactionVector[] vectors;
  boolean[] labels;
  int size;

  ReferenceAccumulator(int capacity) {
    this.vectors = new TransactionVector[capacity];
    this.labels = new boolean[capacity];
  }

  void add(ReferenceItem item) {
    if (size == vectors.length) {
      int newCap = size * 2;
      vectors = Arrays.copyOf(vectors, newCap);
      labels = Arrays.copyOf(labels, newCap);
    }

    vectors[size] = new TransactionVector(item.vector());
    labels[size] = item.isFraud();
    size++;
  }
}
