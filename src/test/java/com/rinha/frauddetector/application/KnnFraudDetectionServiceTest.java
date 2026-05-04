package com.rinha.frauddetector.application;

import com.rinha.frauddetector.domain.FraudDetectionService;
import com.rinha.frauddetector.domain.FraudScore;
import com.rinha.frauddetector.domain.TransactionVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KnnFraudDetectionServiceTest {

  @Test
  void shouldReturnSafeWhenDatasetNotLoaded() {
    FraudDetectionService service = new KnnFraudDetectionService();
    TransactionVector vector = new TransactionVector(new float[14]);

    FraudScore score = service.evaluate(vector);

    assertTrue(score.approved());
    assertEquals(0.0, score.score(), 0.001);
  }

  @Test
  void shouldReturnSafeWhenNullVector() {
    FraudDetectionService service = new KnnFraudDetectionService();
    service.loadDataset(
        new TransactionVector[]{new TransactionVector(new float[14])},
        new boolean[]{false}
    );

    FraudScore score = service.evaluate(null);

    assertTrue(score.approved());
    assertEquals(0.0, score.score(), 0.001);
  }

  @Test
  void shouldDetectFraudWithAllFraudNeighbors() {
    FraudDetectionService service = new KnnFraudDetectionService();

    TransactionVector[] vectors = new TransactionVector[5];
    for (int i = 0; i < 5; i++) {
      vectors[i] = new TransactionVector(new float[14]);
    }

    boolean[] labels = new boolean[]{true, true, true, true, true};
    service.loadDataset(vectors, labels);

    TransactionVector query = new TransactionVector(new float[14]);
    FraudScore score = service.evaluate(query);

    assertFalse(score.approved());
    assertEquals(1.0, score.score(), 0.001);
  }

  @Test
  void shouldDetectLegitWithAllLegitNeighbors() {
    FraudDetectionService service = new KnnFraudDetectionService();

    TransactionVector[] vectors = new TransactionVector[5];
    for (int i = 0; i < 5; i++) {
      vectors[i] = new TransactionVector(new float[14]);
    }

    boolean[] labels = new boolean[]{false, false, false, false, false};
    service.loadDataset(vectors, labels);

    TransactionVector query = new TransactionVector(new float[14]);
    FraudScore score = service.evaluate(query);

    assertTrue(score.approved());
    assertEquals(0.0, score.score(), 0.001);
  }

  @Test
  void shouldCalculateMixedScore() {
    FraudDetectionService service = new KnnFraudDetectionService();

    TransactionVector[] vectors = new TransactionVector[5];
    for (int i = 0; i < 5; i++) {
      vectors[i] = new TransactionVector(new float[14]);
    }

    boolean[] labels = new boolean[]{true, true, false, false, false};
    service.loadDataset(vectors, labels);

    TransactionVector query = new TransactionVector(new float[14]);
    FraudScore score = service.evaluate(query);

    assertTrue(score.approved());
    assertEquals(0.4, score.score(), 0.001);
  }

  @Test
  void shouldHandleEmptyDataset() {
    FraudDetectionService service = new KnnFraudDetectionService();
    service.loadDataset(new TransactionVector[0], new boolean[0]);

    assertFalse(service.isDatasetLoaded());
  }

  @Test
  void shouldHandleNullDataset() {
    FraudDetectionService service = new KnnFraudDetectionService();
    service.loadDataset(null, null);

    assertFalse(service.isDatasetLoaded());
  }
}
