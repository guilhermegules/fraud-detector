package com.rinha.frauddetector.domain;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FraudScoreTest {

  @Test
  void shouldCreateSafeScore() {
    FraudScore score = FraudScore.SAFE;
    assertTrue(score.approved());
    assertEquals(0.0f, score.score(), 0.001f);
  }

  @Test
  void shouldCreateFromFraudCount() {
    FraudScore score = FraudScore.fromFraudCount(0);
    assertTrue(score.approved());
    assertEquals(0.0f, score.score(), 0.001f);
  }

  @Test
  void shouldRejectAtThreshold() {
    FraudScore score = FraudScore.fromFraudCount(3);
    assertFalse(score.approved());
    assertEquals(0.6f, score.score(), 0.001f);
  }

  @Test
  void shouldRejectAboveThreshold() {
    FraudScore score = FraudScore.fromFraudCount(4);
    assertFalse(score.approved());
    assertEquals(0.8f, score.score(), 0.001f);
  }

  @Test
  void shouldClampFraudCount() {
    FraudScore score = FraudScore.fromFraudCount(10);
    assertFalse(score.approved());
    assertEquals(1.0f, score.score(), 0.001f);
  }

  @Test
  void shouldReturnPrecomputedResponse() {
    byte[] response = FraudScore.fromFraudCount(0).responseBytes();
    String body = new String(response, StandardCharsets.UTF_8);
    assertTrue(body.contains("\"approved\":true"));
    assertTrue(body.contains("\"fraud_score\":0.0"));
  }

  @Test
  void shouldReturnPrecomputedRejectResponse() {
    byte[] response = FraudScore.fromFraudCount(5).responseBytes();
    String body = new String(response, StandardCharsets.UTF_8);
    assertTrue(body.contains("\"approved\":false"));
    assertTrue(body.contains("\"fraud_score\":1.0"));
  }
}
