package com.rinha.frauddetector.domain;

public record FraudScore(boolean approved, float score) {

  private static final float THRESHOLD = 0.6f;

  public static FraudScore fromScore(float score) {
    score = Math.clamp(score, 0.0f, 1.0f);
    return new FraudScore(score < THRESHOLD, score);
  }
}
