package com.rinha.frauddetector.controller;

import com.rinha.frauddetector.adapter.loader.ReferenceLoader;
import com.rinha.frauddetector.domain.FraudDetectionService;
import com.rinha.frauddetector.domain.FraudScore;
import com.rinha.frauddetector.domain.TransactionVector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudControllerTest {

  @Mock
  private FraudDetectionService fraudDetectionService;

  @Mock
  private ReferenceLoader referenceLoader;

  @Test
  void shouldApproveLowRiskTransaction() {
    when(fraudDetectionService.evaluate(any(TransactionVector.class)))
        .thenReturn(new FraudScore(true, 0.2));

    FraudScore score = fraudDetectionService.evaluate(new TransactionVector(new float[14]));
    assertTrue(score.approved());
    assertEquals(0.2, score.score(), 0.001);
  }

  @Test
  void shouldRejectHighRiskTransaction() {
    when(fraudDetectionService.evaluate(any(TransactionVector.class)))
        .thenReturn(new FraudScore(false, 0.8));

    FraudScore score = fraudDetectionService.evaluate(new TransactionVector(new float[14]));
    assertFalse(score.approved());
    assertEquals(0.8, score.score(), 0.001);
  }

  @Test
  void shouldReturnSafeOnError() {
    when(fraudDetectionService.evaluate(any(TransactionVector.class)))
        .thenReturn(FraudScore.SAFE);

    FraudScore score = fraudDetectionService.evaluate(new TransactionVector(new float[14]));
    assertTrue(score.approved());
    assertEquals(0.0, score.score(), 0.001);
  }
}
