package com.rinha.frauddetector.domain;

import com.rinha.frauddetector.dto.FraudRequest;

public interface FraudDetectionService {

  FraudScore evaluate(FraudRequest request);

  void loadDataset(TransactionVector[] vectors, boolean[] labels);

  boolean isDatasetLoaded();
}
