package com.rinha.frauddetector.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FraudScoreTest {

  @Test
  void shouldCreateSafeScore() {
    FraudScore score = FraudScore.SAFE;
    assertTrue(score.approved());
    assertEquals(0.0f, score.score(), 0.001f);
  }

  @Test
  void shouldCreateFromScoreLowRisk() {
    FraudScore score = FraudScore.fromScore(0.2f);
    assertTrue(score.approved());
    assertEquals(0.2f, score.score(), 0.001f);
  }

  @Test
  void shouldCreateFromScoreHighRisk() {
    FraudScore score = FraudScore.fromScore(0.7f);
    assertFalse(score.approved());
    assertEquals(0.7f, score.score(), 0.001f);
  }

  @Test
  void shouldClampScoreAboveOne() {
    FraudScore score = FraudScore.fromScore(1.5f);
    assertEquals(1.0f, score.score(), 0.001f);
  }

  @Test
  void shouldClampScoreBelowZero() {
    FraudScore score = FraudScore.fromScore(-0.5f);
    assertEquals(0.0f, score.score(), 0.001f);
  }

  @Test
  void shouldIdentifyFraudulent() {
    FraudScore fraudulent = FraudScore.fromScore(0.9f);
    assertTrue(fraudulent.isFraudulent());
    assertTrue(fraudulent.isHighRisk());
  }

  @Test
  void shouldIdentifySafe() {
    FraudScore safe = FraudScore.fromScore(0.2f);
    assertFalse(safe.isFraudulent());
    assertTrue(safe.isLowRisk());
  }

  @Test
  void shouldIdentifyMediumRisk() {
    FraudScore medium = FraudScore.fromScore(0.6f);
    assertTrue(medium.isMediumRisk());
    assertFalse(medium.isLowRisk());
    assertFalse(medium.isHighRisk());
  }
}
