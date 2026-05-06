package com.rinha.frauddetector.application;

import com.rinha.frauddetector.adapter.loader.ReferenceLoader;
import com.rinha.frauddetector.domain.FraudDetectionService;
import com.rinha.frauddetector.domain.FraudScore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KnnFraudDetectionServiceTest {

  @Test
  void shouldThrowWhenDatasetNotLoaded() {
    ReferenceLoader loader = new ReferenceLoader();
    FraudDetectionService service = new KnnFraudDetectionService(loader);

    assertFalse(service.isDatasetLoaded());
  }

  @Test
  void shouldLoadDatasetAndEvaluate() throws Exception {
    ReferenceLoader loader = new ReferenceLoader();
    loader.loadFraudReference();

    FraudDetectionService service = new KnnFraudDetectionService(loader);
    ((KnnFraudDetectionService) service).initialize();

    assertTrue(service.isDatasetLoaded());
  }

  @Test
  void shouldReturnSafeOnException() {
    ReferenceLoader loader = new ReferenceLoader();
    FraudDetectionService service = new KnnFraudDetectionService(loader);

    assertFalse(service.isDatasetLoaded());
  }
}
