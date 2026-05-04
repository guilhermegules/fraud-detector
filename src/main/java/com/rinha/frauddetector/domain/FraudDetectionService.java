package com.rinha.frauddetector.domain;

public interface FraudDetectionService {

  FraudScore evaluate(TransactionVector vector);

  void loadDataset(TransactionVector[] vectors, boolean[] labels);

  boolean isDatasetLoaded();
}
