package com.rinha.frauddetector.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FraudScoreTest {

  @Test
  void shouldCreateSafeScore() {
    FraudScore score = FraudScore.SAFE;
    assertTrue(score.approved());
    assertEquals(0.0, score.score(), 0.001);
  }

  @Test
  void shouldCreateFromScoreLowRisk() {
    FraudScore score = FraudScore.fromScore(0.3);
    assertTrue(score.approved());
    assertEquals(0.3, score.score(), 0.001);
  }

  @Test
  void shouldCreateFromScoreHighRisk() {
    FraudScore score = FraudScore.fromScore(0.7);
    assertFalse(score.approved());
    assertEquals(0.7, score.score(), 0.001);
  }

  @Test
  void shouldClampScoreAboveOne() {
    FraudScore score = FraudScore.fromScore(1.5);
    assertEquals(1.0, score.score(), 0.001);
  }

  @Test
  void shouldClampScoreBelowZero() {
    FraudScore score = FraudScore.fromScore(-0.5);
    assertEquals(0.0, score.score(), 0.001);
  }

  @Test
  void shouldIdentifyFraudulent() {
    FraudScore fraudulent = FraudScore.fromScore(0.9);
    assertTrue(fraudulent.isFraudulent());
    assertTrue(fraudulent.isHighRisk());
  }

  @Test
  void shouldIdentifySafe() {
    FraudScore safe = FraudScore.fromScore(0.2);
    assertFalse(safe.isFraudulent());
    assertTrue(safe.isLowRisk());
  }

  @Test
  void shouldIdentifyMediumRisk() {
    FraudScore medium = FraudScore.fromScore(0.6);
    assertTrue(medium.isMediumRisk());
    assertFalse(medium.isLowRisk());
    assertFalse(medium.isHighRisk());
  }
}
