package com.rinha.frauddetector.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransactionVectorTest {

  @Test
  void shouldCreateValidVector() {
    float[] features = new float[14];
    for (int i = 0; i < 14; i++) features[i] = 0.5f;

    TransactionVector vector = new TransactionVector(features);
    assertNotNull(vector);
    assertEquals(14, vector.features().length);
  }

  @Test
  void shouldRejectInvalidSize() {
    float[] features = new float[10];
    assertThrows(IllegalArgumentException.class, () -> new TransactionVector(features));
  }

  @Test
  void shouldCalculateDistance() {
    float[] f1 = new float[14];
    float[] f2 = new float[14];
    for (int i = 0; i < 14; i++) {
      f1[i] = 0.0f;
      f2[i] = 1.0f;
    }

    TransactionVector v1 = new TransactionVector(f1);
    TransactionVector v2 = new TransactionVector(f2);

    double distance = v1.distanceTo(v2);
    assertEquals(14.0, distance, 0.001);
  }

  @Test
  void shouldCreateFromRequest() {
    TransactionVector vector =
        TransactionVector.fromRequest(
            100.0, 1, 500.0, 2, 300.0, 10.0, false, true, false, 0.0, "5912", true, 12, 0.2);

    assertNotNull(vector);
    assertEquals(14, vector.features().length);
    assertTrue(vector.features()[0] >= 0.0 && vector.features()[0] <= 1.0);
  }
}
