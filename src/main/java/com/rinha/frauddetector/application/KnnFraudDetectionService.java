package com.rinha.frauddetector.application;

import com.rinha.frauddetector.adapter.loader.ReferenceLoader;
import com.rinha.frauddetector.domain.FraudDetectionService;
import com.rinha.frauddetector.domain.FraudScore;
import com.rinha.frauddetector.domain.TransactionVector;
import com.rinha.frauddetector.dto.FraudRequest;

import jakarta.annotation.PostConstruct;

import java.io.IOException;

public class KnnFraudDetectionService implements FraudDetectionService {

  private BruteForceKNNSearch search;
  private final ReferenceLoader referenceLoader;
  private static final int VECTOR_SIZE = 14;

  public KnnFraudDetectionService(ReferenceLoader referenceLoader) {
    this.referenceLoader = referenceLoader;
  }

  @PostConstruct
  public void initialize() throws IOException {
    referenceLoader.loadFraudReference();
    final var fraudReference = referenceLoader.getFraudReference();
    int count = fraudReference.vectors().length / 14;
    this.search = new BruteForceKNNSearch(fraudReference.vectors(), fraudReference.labels(), count);
  }

  @Override
  public FraudScore evaluate(FraudRequest request) {
    if (search == null) {
      throw new IllegalStateException("Dataset not loaded");
    }

    short[] vector = TransactionVector.toArray(
        request,
        referenceLoader.getNormalizationConstants(),
        referenceLoader.getMccRiskMap()
    );

    try {
      BruteForceKNNSearch.Result result = search.search(vector);
      return FraudScore.fromScore(result.score());
    } catch (Exception e) {
      return FraudScore.SAFE;
    }
  }

  @Override
  public boolean isDatasetLoaded() {
    return search != null;
  }
}
